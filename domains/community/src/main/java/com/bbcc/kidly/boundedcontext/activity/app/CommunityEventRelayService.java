package com.bbcc.kidly.boundedcontext.activity.app;

import com.bbcc.kidly.boundedcontext.activity.app.port.CommunityEventSender;
import com.bbcc.kidly.boundedcontext.activity.app.port.CommunityEventSender.SendFailedException;
import com.bbcc.kidly.boundedcontext.activity.app.port.EventOutbox;
import com.bbcc.kidly.boundedcontext.activity.app.port.EventOutbox.PendingMessage;
import com.bbcc.kidly.boundedcontext.activity.app.port.EventRelayLock;
import com.bbcc.kidly.boundedcontext.activity.domain.ActivityTimes;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 커밋된 아웃박스 행을 kidly.community.events로 보낸다(스케줄러가 부른다). 서버 한 대에서만 돌고(잠금을 못 잡으면 건너뜀) 보내기는
 * 트랜잭션 밖, 결과 반영은 한 건마다다.
 * <ul>
 *   <li>id 순으로 보내고, 보내면 그 행을 바로 지운다(보낸 뒤 지우기 전에 서버가 죽으면 같은 eventId가 한 번 더 갈 수 있다)</li>
 *   <li>순서: 앞 행이 아직 다시 보낼 때가 아니거나 보내다 실패하면 그 주기를 멈춘다(뒤 행이 앞지르지 않게)</li>
 *   <li>실패: 시도 +1, 다음 시각 = 지금 + initial-backoff × 2^(시도−1)(상한 max-backoff). 시도가 max-attempts에 닿으면 격리하고
 *       다음 행으로 넘어간다(그 키의 순서는 여기서 깨질 수 있어 지표 · 로그로 알린다)</li>
 * </ul>
 * 로그는 eventType · eventId · 키 · 실패 종류만 남긴다(본문을 넣지 않는다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityEventRelayService {

    private final EventOutbox outbox;
    private final CommunityEventSender sender;
    private final EventRelayLock lock;
    private final CommunityEventMetrics metrics;
    private final CommunityEventRelayProperties properties;

    /**
     * @return 한 주기 결과. 다른 서버가 쥐고 있어 건너뛰었으면 skipped
     */
    public RelayRun relay() {
        AtomicReference<RelayRun> result = new AtomicReference<>();
        if (!lock.runExclusively(() -> result.set(relayBatch()))) {
            return new RelayRun(0, 0, 0, true);
        }
        return result.get();
    }

    private RelayRun relayBatch() {
        int sent = 0;
        int failed = 0;
        int quarantined = 0;
        for (PendingMessage message : outbox.findPending(properties.batchSize())) {
            LocalDateTime now = ActivityTimes.now();
            if (message.nextAttemptAt().isAfter(now)) {
                break;
            }
            try {
                sender.send(message.key(), message.eventType(), message.eventId(), message.payload(),
                    properties.sendTimeout());
            } catch (SendFailedException e) {
                metrics.sendFailed();
                int attempts = message.attempts() + 1;
                if (attempts >= properties.maxAttempts()) {
                    outbox.quarantine(message.id(), message.eventId(), now, e.failureType());
                    quarantined++;
                    log.error("커뮤니티 이벤트 격리(보내기 상한): eventType={} eventId={} key={} attempts={} error={}",
                        message.eventType(), message.eventId(), message.key(), attempts, e.failureType());
                    continue;
                }
                outbox.recordFailure(message.id(), message.eventId(), now.plus(backoff(attempts)),
                    e.failureType());
                failed++;
                log.warn("커뮤니티 이벤트 보내기 실패: eventType={} eventId={} key={} attempts={} error={}",
                    message.eventType(), message.eventId(), message.key(), attempts, e.failureType());
                break;
            }
            outbox.delete(message.id(), message.eventId());
            sent++;
        }
        return new RelayRun(sent, failed, quarantined, false);
    }

    /**
     * initial-backoff × 2^(시도−1), max-backoff에서 멈춘다.
     */
    Duration backoff(int attempts) {
        Duration max = properties.maxBackoff();
        Duration delay = properties.initialBackoff();
        for (int i = 1; i < attempts && delay.compareTo(max) < 0; i++) {
            delay = delay.multipliedBy(2);
        }
        return delay.compareTo(max) > 0 ? max : delay;
    }

    /**
     * @param failed 실패해 다음 시각으로 미룬 수(그 뒤로는 멈춘다)
     * @param quarantined 시도 상한에 닿아 격리한 수
     */
    public record RelayRun(int sent, int failed, int quarantined, boolean skipped) {
    }
}
