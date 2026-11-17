package com.bbcc.kidly.boundedcontext.member.in.outbox;

import com.bbcc.kidly.boundedcontext.member.app.outbox.MemberOutboxCleanupService;
import com.bbcc.kidly.boundedcontext.member.app.outbox.MemberOutboxRelayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "kidly.kafka.producer.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class MemberOutboxScheduler {

    private final MemberOutboxRelayService relayService;
    private final MemberOutboxCleanupService cleanupService;

    // fixedDelay는 이전 실행이 끝난 뒤부터 주기를 재므로 릴레이가 겹치지 않는다.
    // 첫 실행도 한 주기 뒤로 미뤄, 기동 직후 바로 돌지 않게 한다(테스트는 주기를 길게 둬서 돌지 않는다).
    @Scheduled(fixedDelayString = "${kidly.member.outbox.relay-interval}",
        initialDelayString = "${kidly.member.outbox.relay-interval}")
    public void relay() {
        try {
            relayService.relay();
        } catch (RuntimeException e) {
            log.warn("회원 아웃박스 릴레이가 예외로 끝났습니다. error={}", e.getClass().getSimpleName());
        }
    }

    @Scheduled(cron = "${kidly.member.outbox.cleanup-cron}", zone = "Asia/Seoul")
    public void cleanUp() {
        try {
            cleanupService.cleanUp();
        } catch (RuntimeException e) {
            log.warn("회원 아웃박스 정리가 예외로 끝났습니다. error={}", e.getClass().getSimpleName());
        }
    }
}
