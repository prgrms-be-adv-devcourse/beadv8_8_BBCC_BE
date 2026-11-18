package com.bbcc.kidly.boundedcontext.auth.in.outbox;

import com.bbcc.kidly.boundedcontext.auth.app.outbox.AuthOutboxRelay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "kidly.kafka.producer.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class AuthOutboxScheduler {

    private final AuthOutboxRelay outboxRelay;

    // fixedDelay는 이전 실행이 끝난 뒤부터 주기를 재므로 릴레이가 겹치지 않는다.
    // 첫 실행도 한 주기 뒤로 미뤄, 기동 직후 바로 돌지 않게 한다(테스트는 주기를 길게 둬서 돌지 않는다).
    // DB 오류 메시지에 행 값(본문)이 들어갈 수 있어 로그에는 예외 종류만 남긴다.
    @Scheduled(fixedDelayString = "${kidly.auth.outbox.relay-interval}",
        initialDelayString = "${kidly.auth.outbox.relay-interval}")
    public void relay() {
        try {
            outboxRelay.relay();
        } catch (RuntimeException e) {
            log.warn("인증 아웃박스 릴레이가 예외로 끝났습니다. error={}", e.getClass().getSimpleName());
        }
    }

    @Scheduled(cron = "${kidly.auth.outbox.cleanup-cron}", zone = "Asia/Seoul")
    public void cleanUp() {
        try {
            outboxRelay.cleanUp();
        } catch (RuntimeException e) {
            log.warn("인증 아웃박스 정리가 예외로 끝났습니다. error={}", e.getClass().getSimpleName());
        }
    }
}
