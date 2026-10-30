package com.bbcc.kidly.boundedcontext.order.in.event;

import com.bbcc.kidly.boundedcontext.order.app.order.OrderEventFailures;
import com.bbcc.kidly.boundedcontext.order.app.order.PaymentFailures;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderEventFailureResult;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

/**
 * 재고 차감됨 · 재고 차감 실패 · 결제 실패 처리를 실행하고, 실패하면 재처리용으로 보관한다(#449).
 * 결제 완료 리스너(#353)와 같은 기준: 처리 중 예외는 분류해 로그를 남긴 뒤 그대로 다시 던진다(처리 트랜잭션은 되돌려짐).
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
     * @param process 이벤트 처리. 리스너 트랜잭션이 있으면 거기에 합류하고, 없으면 서비스 트랜잭션에서 커밋까지 끝낸다(#532)
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
            if (saved != null) {
                markKept(e);
            }
            throw e;
        }
    }

    /**
     * 실패를 주문 자체 보관에 남겼다는 표시를 원래 예외에 붙인다. run과 결제 완료 보관(payment_recovery, #594)이 같은 표시를 쓴다.
     */
    static void markKept(Throwable error) {
        error.addSuppressed(new KeptMark());
    }

    /**
     * run이 실패를 보관한 뒤 다시 던진 예외인지(#541). Kafka 리스너는 보관됐으면 처리 완료로 보고 정상 반환한다.
     * 보관 성공 때만 붙이는 표시로 판정하므로, 보관 실패 · 동시 처리에서 진 쪽 · run 밖에서 난 예외(처리 트랜잭션 커밋 실패,
     * 읽기 실패)는 모두 false다.
     */
    static boolean isKept(Throwable error) {
        for (Throwable suppressed : error.getSuppressed()) {
            if (suppressed instanceof KeptMark) {
                return true;
            }
        }
        return false;
    }

    /**
     * 보관 성공 표시. 원래 예외의 종류 · 메시지는 그대로 두고 suppressed로만 붙인다(스택은 남기지 않음).
     */
    private static final class KeptMark extends RuntimeException {

        private KeptMark() {
            super("order_event_failure에 보관함", null, false, false);
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
