package com.bbcc.kidly.boundedcontext.order.in.event;

import com.bbcc.kidly.boundedcontext.order.app.PaymentCompletionService;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderEventFailureService;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.PaymentFailures;
import com.bbcc.kidly.boundedcontext.order.app.order.PaymentRecoveryService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.LostPaymentRecheck;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.MarkPaidCommand;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PaymentRecoveryResult;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentFailedEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * payment.completed를 받아 결제완료 → 판매자별 주문 만들기 → 장바구니 정리를 맡긴다. 셋은 반영 서비스의 트랜잭션 하나로
 * 묶여 중간에 실패하면 모두 되돌려지고, 받은 결제 완료와 실패 이유는 따로 보관한다(#353). 리스너 트랜잭션에 기대지 않는다(#532).
 * payment.failed를 받아 결제 실패 취소를 맡긴다. 이벤트 필드가 바뀌면 여기 변환만 고친다.
 * 받는 곳(#594): Kafka 리스너({@link PaymentEventKafkaListener}), 스위치가 꺼졌을 때 Spring 수신({@link SpringEventReceiver}).
 * 처리 메서드는 트랜잭션을 열지 않는다: 반영 · 보관 · 취소가 각자 트랜잭션에서 커밋까지 끝낸다(#532).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private static final String MISSING_ORDER_ID = "missing-order-id";
    private static final String NOT_SAVED = "not-saved";
    private static final String ALREADY_RECORDED = "already-recorded";
    private static final String RECORD_LOCKED = "record-locked";

    private final OrderService orderService;
    private final PaymentCompletionService paymentCompletion;
    private final PaymentRecoveryService recoveryService;
    private final OrderEventFailureService failureService;

    /**
     * 처리 중 예외는 분류해 로그를 남긴 뒤 그대로 다시 던진다(반영 트랜잭션은 되돌려짐). 반영은 커밋까지 끝낸 뒤 돌아오므로
     * 커밋 단계 실패(주문 버전 충돌 등)도 여기서 같은 기준으로 나눈다(#532).
     * 낙관적 락 · 배송 (order_id, seller_id) 유니크 위반 = 동시 중복 처리에서 진 쪽 → WARN
     * (이긴 쪽이 주문을 취소했으면 대신 환불 확인 WARN, #519). 주문을 다시 읽어 결과가 났으면 처리 완료 표시를 붙이고,
     * 아직 결제대기면(장바구니 줄 동시 변경 등으로 반영이 통째로 되돌려짐) 붙이지 않는다(#594),
     * 그 밖 = 돈은 빠졌는데 결제완료가 안 됨 → 재처리용으로 보관하고(결제 불일치는 재처리 불가) 보관 결과를 붙여 ERROR 한 줄.
     * 보관에 성공하면 원래 예외에 보관 표시를 붙인다(Kafka 리스너가 처리 완료로 본다, #594).
     */
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
        try {
            paymentCompletion.applyForEvent(paid);
        } catch (RuntimeException e) {
            if (PaymentFailures.isLostConcurrentDuplicate(e)) {
                LostPaymentRecheck recheck = recheckLost(paid, e);
                if (recheck != LostPaymentRecheck.PAID_AFTER_CANCEL) {
                    log.warn("결제 완료 처리 실패(동시 중복 처리에서 진 쪽): event={} "
                        + "reason=lost-concurrent-duplicate orderId={} paymentId={}",
                        eventType, paid.orderId(), paid.paymentId(), e);
                }
                // 결과가 이미 났으면(취소 · 결제완료 등) 다시 처리해도 아무것도 안 하므로 처리 완료로 봐도 된다는 표시를 붙인다.
                // 아직 결제대기거나 다시 읽지 못했으면 붙이지 않는다(Kafka가 다시 처리, #594)
                if (recheck == LostPaymentRecheck.PAID_AFTER_CANCEL || recheck == LostPaymentRecheck.SETTLED) {
                    EventFailureKeeper.markHandled(e);
                }
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
                recoveryStatus = recoveryStatusOf(recordError);
            }
            log.error("결제 완료 처리 실패: event={} reason=processing-failed orderId={} paymentId={} "
                    + "recoveryStatus={} attemptCount={} failureCode={}",
                eventType, paid.orderId(), paid.paymentId(), recoveryStatus,
                saved == null ? null : saved.attemptCount(), saved == null ? null : saved.failureCode(), e);
            if (saved != null) {
                EventFailureKeeper.markHandled(e);
            }
            throw e;
        }
    }

    /**
     * 결제 완료가 마지막 재시도까지 실패해 DLT로 가기 직전이다(#632). 보관 없이 다시 던진 실패(동시 처리에서 진 쪽인데 아직
     * 결제대기 · 보관 실패)를 처리 실패와 같은 형식으로 payment_recovery에 보관한다. 보관되면 결제 마감 만료가 이 주문을 취소하지
     * 않고 관리자가 재처리 · 취소로 정한다. 보관하면 보관 결과를 붙여 ERROR 한 줄 · true(DLT로 보내지 않음), 보관이 실패하면
     * 보관 오류를 원래 예외에 붙이고 false(지금처럼 DLT, 로그는 DLT 한 줄).
     */
    public boolean keepAfterRetries(PaymentCompletedEvent event, RuntimeException error) {
        if (event.data() == null || event.data().orderId() == null) {
            return false;
        }
        MarkPaidCommand paid = toCommand(event);
        PaymentRecoveryResult saved;
        try {
            saved = recoveryService.recordFailure(paid, error);
        } catch (RuntimeException recordError) {
            error.addSuppressed(recordError);
            return false;
        }
        log.error("결제 완료 처리 실패: event={} reason=retries-exhausted orderId={} paymentId={} "
                + "recoveryStatus={} attemptCount={} failureCode={}",
            event.eventType(), paid.orderId(), paid.paymentId(), saved.status(), saved.attemptCount(),
            saved.failureCode(), error);
        return true;
    }

    // 주문을 새 트랜잭션에서 다시 읽는다. 이긴 쪽이 주문을 취소했으면 서비스가 환불 확인 WARN을 남긴다(#519).
    // 다시 읽기가 실패해도 원래 예외를 그대로 던지도록 읽기 오류는 원래 예외에 붙이고(suppressed) 아직 결제대기로 본다(다시 처리)
    private LostPaymentRecheck recheckLost(MarkPaidCommand paid, RuntimeException e) {
        try {
            return orderService.recheckLostPaymentCompleted(paid);
        } catch (RuntimeException readError) {
            e.addSuppressed(readError);
            return LostPaymentRecheck.STILL_PENDING;
        }
    }

    // 보관하지 못한 이유를 운영자가 구분하게: 이미 보관됨(동시 실패의 늦은 쪽) · 기록 잠김(재처리 · 취소 중) · 그 밖
    private static String recoveryStatusOf(RuntimeException recordError) {
        if (PaymentFailures.isAlreadyRecorded(recordError)) {
            return ALREADY_RECORDED;
        }
        return PaymentFailures.isRecordLocked(recordError) ? RECORD_LOCKED : NOT_SAVED;
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
     * 취소 결과는 서비스가 "주문 취소" 한 줄로 남긴다. 처리에 실패하면 재처리용으로 보관한다(#449).
     */
    public void onPaymentFailed(PaymentFailedEvent event) {
        Long orderId = toOrderId(event);
        if (!EventGuard.accept("결제", event, orderId == null ? MISSING_ORDER_ID : null)) {
            return;
        }
        EventFailureKeeper.run(event.eventType(), orderId, () -> orderService.cancelForPaymentFailure(orderId),
            error -> failureService.recordPaymentFailedFailure(orderId, error));
    }

    private Long toOrderId(PaymentFailedEvent event) {
        return event.data() == null ? null : event.data().orderId();
    }
}
