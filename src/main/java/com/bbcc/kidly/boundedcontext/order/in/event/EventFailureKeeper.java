package com.bbcc.kidly.boundedcontext.order.in.event;

import com.bbcc.kidly.boundedcontext.order.app.order.OrderEventFailures;
import com.bbcc.kidly.boundedcontext.order.app.order.PaymentFailures;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderEventFailureResult;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

/**
 * 재고 차감됨 · 재고 차감 실패 · 결제 실패 처리를 실행하고, 실패하면 재처리용으로 보관한다(#449).
 * 결제 완료 리스너(#353)와 같은 기준: 처리 중 예외는 분류해 로그를 남긴 뒤 그대로 다시 던진다(리스너 트랜잭션은 되돌려짐).
 * 낙관적 락 = 동시 처리에서 진 쪽(이긴 쪽이 이미 처리함) → 보관하지 않고 WARN, 그 밖 → 보관하고 보관 결과를 붙여 ERROR 한 줄.
 */
@Slf4j
final class EventFailureKeeper {

    private static final String NOT_SAVED = "not-saved";
    private static final String ALREADY_RECORDED = "already-recorded";
    private static final String RECORD_LOCKED = "record-locked";

    private EventFailureKeeper() {
    }

    /**
     * @param eventType 받은 이벤트 타입(로그용)
     * @param process 이벤트 처리. 리스너 트랜잭션 안에서 실행된다
     * @param record 실패를 새 트랜잭션에 보관한다
     */
    static void run(String eventType, Long orderId, Runnable process,
        Function<RuntimeException, OrderEventFailureResult> record) {
        try {
            process.run();
        } catch (RuntimeException e) {
            if (PaymentFailures.isLostConcurrentDuplicate(e)) {
                log.warn("이벤트 처리 실패(동시 처리에서 진 쪽): event={} reason=lost-concurrent-duplicate orderId={}",
                    eventType, orderId, e);
                throw e;
            }
            OrderEventFailureResult saved = null;
            String failureStatus;
            try {
                saved = record.apply(e);
                failureStatus = saved.status();
            } catch (RuntimeException recordError) {
                // 보관이 실패해도 원래 예외를 그대로 던지도록 보관 오류는 원래 예외에 붙인다(suppressed)
                e.addSuppressed(recordError);
                failureStatus = failureStatusOf(recordError);
            }
            log.error("이벤트 처리 실패: event={} reason=processing-failed orderId={} failureStatus={} attemptCount={} "
                    + "failureCode={}",
                eventType, orderId, failureStatus, saved == null ? null : saved.attemptCount(),
                saved == null ? null : saved.failureCode(), e);
            throw e;
        }
    }

    // 보관하지 못한 이유를 운영자가 구분하게: 이미 보관됨(동시 실패의 늦은 쪽) · 기록 잠김(재처리 · 종료 중) · 그 밖
    private static String failureStatusOf(RuntimeException recordError) {
        if (OrderEventFailures.isAlreadyRecorded(recordError)) {
            return ALREADY_RECORDED;
        }
        return PaymentFailures.isRecordLocked(recordError) ? RECORD_LOCKED : NOT_SAVED;
    }
}
