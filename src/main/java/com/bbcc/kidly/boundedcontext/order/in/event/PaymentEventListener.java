package com.bbcc.kidly.boundedcontext.order.in.event;

import com.bbcc.kidly.boundedcontext.order.app.cart.CartService;
import com.bbcc.kidly.boundedcontext.order.app.delivery.DeliveryService;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.MarkPaidCommand;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentFailedEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * payment.completed를 받아 결제완료 → 판매자별 주문 만들기 → 장바구니 정리를 차례로 맡긴다.
 * 리스너 트랜잭션 하나로 묶여 중간에 실패하면 모두 되돌린다.
 * payment.failed를 받아 결제 실패 취소를 맡긴다. 이벤트 필드가 바뀌면 여기 변환만 고친다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private static final String DELIVERY_UNIQUE = "uk_delivery_order_seller";

    private final OrderService orderService;
    private final DeliveryService deliveryService;
    private final CartService cartService;

    /**
     * 처리 중 예외는 분류해 로그를 남긴 뒤 그대로 다시 던진다(트랜잭션은 그대로 되돌려짐).
     * 낙관적 락 · 배송 (order_id, seller_id) 유니크 위반 = 동시 중복 처리에서 진 쪽(이긴 쪽이 이미 처리함) → WARN,
     * 그 밖 = 돈은 빠졌는데 결제완료가 안 됨 → ERROR(결제 환불 연결 뒤 WARN으로 내릴 예정).
     * 주문 버전 충돌은 메서드가 끝난 뒤 커밋 때 나므로, 트랜잭션 완료 콜백에서 되돌려졌으면 WARN을 남긴다
     * (본문에서 이미 로그를 남긴 경우는 제외).
     */
    @DomainEventListener
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        if (event.data() == null || event.data().orderId() == null) {
            log.warn("결제 이벤트 무시: event=payment.completed reason=missing-order-id");
            return;
        }
        MarkPaidCommand paid = toCommand(event);
        if (paid.paymentId() == null) {
            // 돈은 이미 빠졌으므로 막지 않고 결제완료로 바꾼다(막으면 결제 마감 만료로 취소됨). 결제 쪽 계약 위반이라 ERROR
            log.error("결제 완료 결제 ID 없음: event=payment.completed reason=missing-payment-id orderId={}", paid.orderId());
        }
        AtomicBoolean logged = new AtomicBoolean(false);
        warnIfRolledBackAtCommit(paid, logged);
        try {
            if (!orderService.markPaid(paid)) {
                return;
            }
            deliveryService.createFor(paid.orderId());
            cartService.clearOrdered(paid.orderId());
        } catch (RuntimeException e) {
            logged.set(true);
            if (isLostConcurrentDuplicate(e)) {
                log.warn("결제 완료 처리 실패(동시 중복 처리에서 진 쪽): event=payment.completed orderId={} paymentId={}",
                    paid.orderId(), paid.paymentId(), e);
            } else {
                log.error("결제 완료 처리 실패: event=payment.completed orderId={} paymentId={}",
                    paid.orderId(), paid.paymentId(), e);
            }
            throw e;
        }
    }

    private static void warnIfRolledBackAtCommit(MarkPaidCommand paid, AtomicBoolean logged) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK && !logged.get()) {
                    log.warn("커밋 단계에서 되돌려짐 — 동시 처리에서 진 쪽일 가능성: event=payment.completed "
                        + "orderId={} paymentId={}", paid.orderId(), paid.paymentId());
                }
            }
        });
    }

    private static boolean isLostConcurrentDuplicate(RuntimeException error) {
        if (error instanceof OptimisticLockingFailureException) {
            return true;
        }
        if (!(error instanceof DataIntegrityViolationException)) {
            return false;
        }
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(DELIVERY_UNIQUE)) {
                return true;
            }
        }
        return false;
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
     * 실패 사유(reasonCode)는 업무 정보라 여기서 INFO로 남기고 주문에는 넘기지 않는다. 취소 결과는 서비스가 남긴다.
     */
    @DomainEventListener
    public void onPaymentFailed(PaymentFailedEvent event) {
        Long orderId = toOrderId(event);
        if (orderId == null) {
            log.warn("결제 이벤트 무시: event=payment.failed reason=missing-order-id");
            return;
        }
        log.info("결제 실패 수신: event=payment.failed orderId={} reason={}", orderId, event.data().reasonCode());
        orderService.cancelForPaymentFailure(orderId);
    }

    private Long toOrderId(PaymentFailedEvent event) {
        return event.data() == null ? null : event.data().orderId();
    }
}
