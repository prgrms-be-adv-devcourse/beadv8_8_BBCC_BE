package com.bbcc.kidly.boundedcontext.order.in.event;

import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.PaymentFailures;
import com.bbcc.kidly.boundedcontext.order.app.order.PaymentRecoveryService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.MarkPaidCommand;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PaymentRecoveryResult;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentFailedEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * payment.completed를 받아 결제완료 → 판매자별 주문 만들기 → 장바구니 정리를 차례로 맡긴다.
 * 리스너 트랜잭션 하나로 묶여 중간에 실패하면 모두 되돌리고, 받은 결제 완료와 실패 이유는 따로 보관한다(#353).
 * payment.failed를 받아 결제 실패 취소를 맡긴다. 이벤트 필드가 바뀌면 여기 변환만 고친다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private static final String MISSING_ORDER_ID = "missing-order-id";
    private static final String NOT_SAVED = "not-saved";
    private static final String ALREADY_RECORDED = "already-recorded";

    private final OrderService orderService;
    private final PaymentCompletion paymentCompletion;
    private final PaymentRecoveryService recoveryService;

    /**
     * 처리 중 예외는 분류해 로그를 남긴 뒤 그대로 다시 던진다(트랜잭션은 그대로 되돌려짐).
     * 낙관적 락 · 배송 (order_id, seller_id) 유니크 위반 = 동시 중복 처리에서 진 쪽(이긴 쪽이 이미 처리함) → WARN,
     * 그 밖 = 돈은 빠졌는데 결제완료가 안 됨 → 재처리용으로 보관하고(결제 불일치는 재처리 불가) 보관 결과를 붙여 ERROR 한 줄.
     * 주문 버전 충돌은 메서드가 끝난 뒤 커밋 때 나므로, 트랜잭션 완료 콜백에서 되돌려졌으면 WARN을 남긴다
     * (본문에서 이미 로그를 남긴 경우는 제외).
     */
    @DomainEventListener
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        boolean missingOrderId = event.data() == null || event.data().orderId() == null;
        if (!EventGuard.accept("결제", event, missingOrderId ? MISSING_ORDER_ID : null)) {
            return;
        }
        String eventType = event.eventType();
        MarkPaidCommand paid = toCommand(event);
        if (paid.paymentId() == null) {
            // 돈은 이미 빠졌으므로 막지 않고 결제완료로 바꾼다(막으면 결제 마감 만료로 취소됨). 결제 쪽 계약 위반이라 ERROR
            log.error("결제 완료 결제 ID 없음: event={} reason=missing-payment-id orderId={}", eventType, paid.orderId());
        }
        if (event.data().paidAt() == null) {
            // 결제 시각은 이벤트 값이 아니라 처리 시각(now)을 쓰므로 막지 않는다. 결제 쪽 계약 위반이라 ERROR(사유별 한 줄)
            log.error("결제 완료 결제 시각 없음: event={} reason=missing-paid-at orderId={}", eventType, paid.orderId());
        }
        AtomicBoolean logged = new AtomicBoolean(false);
        warnIfRolledBackAtCommit(eventType, paid, logged);
        try {
            paymentCompletion.apply(paid);
        } catch (RuntimeException e) {
            logged.set(true);
            if (PaymentFailures.isLostConcurrentDuplicate(e)) {
                log.warn("결제 완료 처리 실패(동시 중복 처리에서 진 쪽): event={} "
                    + "reason=lost-concurrent-duplicate orderId={} paymentId={}",
                    eventType, paid.orderId(), paid.paymentId(), e);
                throw e;
            }
            PaymentRecoveryResult saved = null;
            String recoveryStatus;
            try {
                saved = recoveryService.recordFailure(paid, e);
                recoveryStatus = saved.status();
            } catch (RuntimeException recordError) {
                // 보관이 실패해도 원래 예외를 그대로 던지도록 보관 오류는 원래 예외에 붙인다(suppressed).
                // 같은 주문의 결제 완료 두 건이 동시에 실패하면 늦은 쪽은 기록 유니크에 걸려 시도 횟수가 하나 덜 셀 수 있다 —
                // 기록은 1건으로 남고 재처리에는 지장이 없어 다시 읽어 갱신하지 않고, 로그에는 이미 보관됨으로 남긴다
                e.addSuppressed(recordError);
                recoveryStatus = PaymentFailures.isAlreadyRecorded(recordError) ? ALREADY_RECORDED : NOT_SAVED;
            }
            log.error("결제 완료 처리 실패: event={} reason=processing-failed orderId={} paymentId={} "
                    + "recoveryStatus={} attemptCount={} failureCode={}",
                eventType, paid.orderId(), paid.paymentId(), recoveryStatus,
                saved == null ? null : saved.attemptCount(), saved == null ? null : saved.failureCode(), e);
            throw e;
        }
    }

    private static void warnIfRolledBackAtCommit(String eventType, MarkPaidCommand paid, AtomicBoolean logged) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK && !logged.get()) {
                    log.warn("커밋 단계에서 되돌려짐 — 동시 처리에서 진 쪽일 가능성: event={} "
                        + "reason=rolled-back-at-commit orderId={} paymentId={}",
                        eventType, paid.orderId(), paid.paymentId());
                }
            }
        });
    }

    /**
     * 주문 ID · 결제 ID · 회원 · 결제 금액 · 주문상품별 금액을 쓴다. 회원 · 구성 · 금액은 주문이 대조한다(다르면 예외).
     * 결제 시각은 주문이 지금 시각으로 정한다. 주문상품 목록이 비면(null) 빈 목록으로 넘긴다(구성 불일치).
     */
    private MarkPaidCommand toCommand(PaymentCompletedEvent event) {
        PaymentCompletedEvent.Data data = event.data();
        List<MarkPaidCommand.Item> items = data.items() == null ? List.of() : data.items().stream()
            .map(item -> item == null ? null : new MarkPaidCommand.Item(item.orderItemId(), item.amount()))
            .toList();
        return new MarkPaidCommand(data.orderId(), data.paymentId(), data.memberId(), data.amount(), items);
    }

    /**
     * 실패 사유(reasonCode)는 주문에 넘기지 않고 로그에도 남기지 않는다(결제 기록 · 관리자 결제 조회에서 확인).
     * 취소 결과는 서비스가 "주문 취소" 한 줄로 남긴다.
     */
    @DomainEventListener
    public void onPaymentFailed(PaymentFailedEvent event) {
        Long orderId = toOrderId(event);
        if (!EventGuard.accept("결제", event, orderId == null ? MISSING_ORDER_ID : null)) {
            return;
        }
        orderService.cancelForPaymentFailure(orderId);
    }

    private Long toOrderId(PaymentFailedEvent event) {
        return event.data() == null ? null : event.data().orderId();
    }
}
