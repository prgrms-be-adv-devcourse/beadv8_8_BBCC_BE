package com.bbcc.kidly.boundedcontext.payment.in.scheduler;

import com.bbcc.kidly.boundedcontext.payment.app.PaymentOutboxCleanupService;
import com.bbcc.kidly.boundedcontext.payment.app.PaymentOutboxProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 결제 이벤트 아웃박스 정리(#550): 보관 기간이 지난 보낸 메시지를 지운다. 실행 시각은 kidly.payment.outbox.cleanup-cron
 * ("-"면 끔). DB만 쓰므로 Kafka 보내기가 꺼져 있어도 있다.
 * 실패는 WARN만 남긴다. 예외 메시지에 행 값(본문)이 들어갈 수 있어 예외 종류만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentOutboxCleanupScheduler {

    private final PaymentOutboxCleanupService cleanupService;
    // 보관 기간의 바인딩 검증(1초 이상)이 @Scheduled 등록보다 먼저 돌아 키 이름이 나오는 오류로 실패하게 하려고 주입한다
    private final PaymentOutboxProperties properties;

    @Scheduled(cron = "${kidly.payment.outbox.cleanup-cron}", zone = "Asia/Seoul")
    public void cleanUp() {
        try {
            int deleted = cleanupService.cleanUp();
            log.info("결제 아웃박스 정리: deleted={} retention={}", deleted, properties.retention());
        } catch (RuntimeException e) {
            log.warn("결제 아웃박스 정리가 예외로 끝났습니다. error={}", e.getClass().getSimpleName());
        }
    }
}
