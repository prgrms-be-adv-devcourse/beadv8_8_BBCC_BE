package com.bbcc.kidly.boundedcontext.order.in.scheduler;

import com.bbcc.kidly.boundedcontext.order.app.outbox.OrderOutboxCleanupService;
import com.bbcc.kidly.boundedcontext.order.app.outbox.OrderOutboxProperties;
import com.bbcc.kidly.boundedcontext.order.app.port.SingleServerJobRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 주문 이벤트 아웃박스 정리(#540): 보관 기간이 지난 보낸 메시지를 지운다. 실행 시각은 kidly.order.outbox.cleanup-cron
 * (기본값은 order.yml, "-"면 끔). DB만 쓰므로 Kafka 보내기가 꺼져 있어도 있다.
 * 실패는 WARN만 남긴다. 예외 메시지에 행 값(본문)이 들어갈 수 있어 예외 종류만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderOutboxCleanupScheduler {

    static final String CLEANUP_JOB = "outbox-cleanup";

    private final OrderOutboxCleanupService cleanupService;
    // 보관 기간의 바인딩 검증(1초 이상)이 @Scheduled 등록보다 먼저 돌아 키 이름이 나오는 오류로 실패하게 하려고 주입한다
    private final OrderOutboxProperties properties;
    private final SingleServerJobRunner jobRunner;

    @Scheduled(cron = "${kidly.order.outbox.cleanup-cron}")
    public void cleanUp() {
        try {
            int[] deleted = new int[1];
            // 한 번에 한 서버에서만(#564). 다른 서버가 돌고 있으면 러너가 DEBUG 한 줄 남기고 건너뛴다
            if (jobRunner.run(CLEANUP_JOB, () -> deleted[0] = cleanupService.cleanUp())) {
                log.info("아웃박스 정리: deleted={} retention={}", deleted[0], properties.retention());
            }
        } catch (RuntimeException e) {
            log.warn("아웃박스 정리 실패: error={}", e.getClass().getSimpleName());
        }
    }
}
