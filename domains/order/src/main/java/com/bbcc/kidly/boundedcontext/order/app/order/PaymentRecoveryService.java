package com.bbcc.kidly.boundedcontext.order.app.order;

import com.bbcc.kidly.boundedcontext.order.app.OrderActor;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.MarkPaidCommand;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PaymentRecoveryResult;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderStatusHistoryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.PaymentRecoveryRepository;
import com.bbcc.kidly.boundedcontext.order.domain.OrderErrorCode;
import com.bbcc.kidly.boundedcontext.order.domain.history.OrderStatusHistory;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.recovery.PaymentRecovery;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 완료 반영 실패 보관 · 관리자 재처리 · 취소로 종료(#353).
 * 로그: 재처리 · 취소 결과마다 INFO 한 줄. 실패 보관은 결제 완료 리스너가 실패 로그 한 줄에 함께 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRecoveryService {

    private final PaymentRecoveryRepository recoveryRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final OrderStatusHistoryRepository historyRepository;

    /**
     * 받은 결제 완료 내용과 실패 이유를 주문당 1건 보관한다(있으면 시도 +1 · 이유 갱신).
     * 리스너 트랜잭션이 되돌려져도 남도록 새 트랜잭션에서 저장한다. 그 트랜잭션이 주문 행을 바꾼 채 열려 있으므로
     * 여기서는 주문을 읽지도 잠그지도 않는다(기록 테이블만 쓴다). 기존 기록은 기다리지 않는 잠금으로 읽는다: 재처리 · 취소가
     * 그 기록을 잠근 채 이 주문 행을 기다리면, 같은 스레드의 리스너 트랜잭션이 쥔 주문 행 때문에 DB가 못 보는 순환 대기가 되므로
     * 바로 실패해 리스너 트랜잭션을 되돌린다(재처리가 처리 중이라 보관할 필요도 없다).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentRecoveryResult recordFailure(MarkPaidCommand paid, RuntimeException error) {
        LocalDateTime now = LocalDateTime.now();
        String failureCode = PaymentFailures.failureCode(error);
        boolean retryable = PaymentFailures.isRetryable(error);
        Optional<PaymentRecovery> existing = recoveryRepository.findByOrderIdForUpdateNoWait(paid.orderId());
        PaymentRecovery.Status from = existing.map(PaymentRecovery::getStatus).orElse(null);
        PaymentRecovery recovery = existing
            .map(found -> {
                found.recordFailure(failureCode, retryable, now);
                return found;
            })
            .orElseGet(() -> recoveryRepository.save(PaymentRecovery.open(paid.orderId(), paid.paymentId(),
                paid.memberId(), paid.amount(), toPaidItems(paid.items()), failureCode, retryable, now)));
        // 상태가 바뀔 때(새 기록 · NOT_RETRYABLE로)만 이력을 남긴다. 시도 횟수만 늘면 남기지 않는다
        if (recovery.getStatus() != from) {
            recordHistory(OrderActor.event(OrderActor.PAYMENT_COMPLETED), recovery, from, failureCode, now);
        }
        return PaymentRecoveryResult.from(recovery);
    }

    /**
     * 최근 기록부터. statusName이 null이면 모든 상태.
     */
    @Transactional(readOnly = true)
    public Page<PaymentRecoveryResult> getRecoveries(String statusName, int page, int size) {
        PaymentRecovery.Status status = toStatus(statusName);
        PageRequest pageable = PageRequest.of(page, size);
        // 저장소는 offset(page × size)이 int를 넘으면 조회하지 못하므로(500), 조회 없이 빈 페이지로 답한다
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            long total = status == null ? recoveryRepository.count() : recoveryRepository.countByStatus(status);
            return new PageImpl<>(List.of(), pageable, total);
        }
        Page<PaymentRecovery> recoveries = status == null
            ? recoveryRepository.findAllByOrderByIdDesc(pageable)
            : recoveryRepository.findAllByStatusOrderByIdDesc(status, pageable);
        return recoveries.map(PaymentRecoveryResult::from);
    }

    @Transactional(readOnly = true)
    public PaymentRecoveryResult getRecovery(Long recoveryId) {
        return PaymentRecoveryResult.from(find(recoveryId));
    }

    /**
     * 보관한 결제 완료로 결제완료 → 배송 생성 → 장바구니 정리를 한 트랜잭션으로 다시 실행한다.
     * 실패하면 이 트랜잭션은 전부 되돌려지고, PaymentRecoveryRetry가 새 트랜잭션에서 시도 횟수를 센다.
     * 주문이 이미 결제완료면 다시 반영하지 않고 복구로 끝낸다: 결제완료 · 배송 생성 · 장바구니 정리는 늘 한 트랜잭션으로
     * 함께 커밋되므로(리스너 · 재처리 모두), 결제완료인데 배송이 없는 주문은 생기지 않는다.
     * 주문이 이미 취소됐으면(기록 저장과 만료가 경합) 결제완료로 되돌리지 않고 기록을 취소로 끝낸다.
     *
     * @param applyPaid 결제 완료 반영(결제완료 → 배송 생성 → 장바구니 정리). 기능끼리 서로 부르지 않으므로 in이 넘긴다
     */
    @Transactional
    public PaymentRecoveryResult recover(Long recoveryId, Long adminId, Consumer<MarkPaidCommand> applyPaid) {
        PaymentRecovery recovery = findForUpdate(recoveryId);
        switch (recovery.getStatus()) {
            case RECOVERED -> {
                return PaymentRecoveryResult.from(recovery);
            }
            case CANCELLED -> throw new BusinessException(OrderErrorCode.RECOVERY_CLOSED);
            case NOT_RETRYABLE -> throw new BusinessException(OrderErrorCode.RECOVERY_NOT_RETRYABLE);
            default -> {
            }
        }
        Order.Status orderStatus = orderStatusOf(recovery);
        if (orderStatus == Order.Status.PENDING_PAYMENT) {
            applyPaid.accept(toCommand(recovery));
        }
        // 결제 완료 반영 뒤의 시각: 이력에서 기록 종료가 주문 결제완료 · 배송 생성 다음에 오게 한다
        LocalDateTime now = LocalDateTime.now();
        PaymentRecovery.Status from = recovery.getStatus();
        if (orderStatus == Order.Status.CANCELLED) {
            recovery.cancel(now);
        } else {
            recovery.recover(now);
        }
        recordHistory(OrderActor.admin(adminId, OrderActor.RECOVERY_RETRY), recovery, from, null, now);
        log.info("결제 반영 재처리: recoveryId={} orderId={} result={}",
            recovery.getId(), recovery.getOrderId(), recovery.getStatus());
        return PaymentRecoveryResult.from(recovery);
    }

    /**
     * 재처리가 다시 실패했다. 아직 종료되지 않은 기록만 시도 +1 · 이유를 바꾼다(동시에 다른 재처리가 복구했으면 그대로 둠).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentRecoveryResult recordRetryFailure(Long recoveryId, Long adminId, RuntimeException error) {
        PaymentRecovery recovery = find(recoveryId);
        if (recovery.isClosed()) {
            return PaymentRecoveryResult.from(recovery);
        }
        LocalDateTime now = LocalDateTime.now();
        PaymentRecovery.Status from = recovery.getStatus();
        recovery.recordFailure(PaymentFailures.failureCode(error), PaymentFailures.isRetryable(error), now);
        if (recovery.getStatus() != from) {
            recordHistory(OrderActor.admin(adminId, OrderActor.RECOVERY_RETRY), recovery, from,
                recovery.getFailureCode(), now);
        }
        log.info("결제 반영 재처리: recoveryId={} orderId={} result={} attemptCount={} failureCode={}",
            recovery.getId(), recovery.getOrderId(), recovery.getStatus(), recovery.getAttemptCount(),
            recovery.getFailureCode());
        return PaymentRecoveryResult.from(recovery);
    }

    /**
     * 주문을 결제 실패 사유로 취소하고(order.cancelled → 결제 환불 · 재고 복구) 기록을 취소로 끝낸다.
     * 주문이 이미 취소됐으면 다시 취소하지 않는다(이벤트 없음). 이미 결제가 반영된 주문은 취소하지 않는다(409).
     */
    @Transactional
    public PaymentRecoveryResult cancel(Long recoveryId, Long adminId) {
        PaymentRecovery recovery = findForUpdate(recoveryId);
        if (recovery.getStatus() == PaymentRecovery.Status.CANCELLED) {
            return PaymentRecoveryResult.from(recovery);
        }
        if (recovery.getStatus() == PaymentRecovery.Status.RECOVERED) {
            throw new BusinessException(OrderErrorCode.RECOVERY_CLOSED);
        }
        Order.Status orderStatus = orderStatusOf(recovery);
        if (orderStatus == Order.Status.PAID) {
            throw new BusinessException(OrderErrorCode.RECOVERY_ORDER_PAID);
        }
        OrderActor admin = OrderActor.admin(adminId, OrderActor.RECOVERY_CANCEL);
        if (orderStatus == Order.Status.PENDING_PAYMENT) {
            orderService.cancelForPaymentFailure(recovery.getOrderId(), admin);
        }
        LocalDateTime now = LocalDateTime.now();
        PaymentRecovery.Status from = recovery.getStatus();
        recovery.cancel(now);
        recordHistory(admin, recovery, from, null, now);
        log.info("결제 반영 취소로 종료: recoveryId={} orderId={} result={}",
            recovery.getId(), recovery.getOrderId(), recovery.getStatus());
        return PaymentRecoveryResult.from(recovery);
    }

    private void recordHistory(OrderActor actor, PaymentRecovery recovery, PaymentRecovery.Status from, String reason,
        LocalDateTime now) {
        historyRepository.save(actor.history(recovery.getOrderId(), OrderStatusHistory.TargetType.PAYMENT_RECOVERY,
            recovery.getId(), from, recovery.getStatus(), reason, now));
    }

    // in은 도메인 enum을 쓰지 않으므로 상태 이름은 여기서 바꾼다. 이름이 정확히 같아야 한다(소문자 · 모르는 값은 400)
    private static PaymentRecovery.Status toStatus(String statusName) {
        if (statusName == null) {
            return null;
        }
        try {
            return PaymentRecovery.Status.valueOf(statusName);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(OrderErrorCode.INVALID_RECOVERY_STATUS);
        }
    }

    private PaymentRecovery find(Long recoveryId) {
        return recoveryRepository.findById(recoveryId)
            .orElseThrow(() -> new BusinessException(OrderErrorCode.RECOVERY_NOT_FOUND));
    }

    /**
     * 재처리 · 취소는 맨 처음에 기록 행을 잠가 읽는다. 재처리는 장바구니 정리 중 자동 flush로 주문 행을 기록보다 먼저 갱신하고,
     * 취소는 커밋 때 기록 → 주문 순서로 갱신해서, 동시에 오면 잠금 순서가 엇갈려 데드락이 났다(#353 CI). 기록 잠금을 먼저
     * 잡으면 둘이 차례로 실행되고, 늦은 쪽은 닫힌 기록을 보고 기존 상태 표대로 409로 끝난다.
     */
    private PaymentRecovery findForUpdate(Long recoveryId) {
        return recoveryRepository.findByIdForUpdate(recoveryId)
            .orElseThrow(() -> new BusinessException(OrderErrorCode.RECOVERY_NOT_FOUND));
    }

    // 기록은 주문이 있을 때만 생기고 주문은 지우지 않는다. 그래도 없으면 불변식 위반이라 예외로 알린다
    private Order.Status orderStatusOf(PaymentRecovery recovery) {
        return orderRepository.findById(recovery.getOrderId())
            .map(Order::getStatus)
            .orElseThrow(() -> new IllegalStateException("기록의 주문이 없습니다: orderId=" + recovery.getOrderId()));
    }

    // 결제 쪽이 빈 칸(null)으로 보낸 주문상품은 보관할 수 없어 뺀다(그런 결제 완료는 구성 불일치라 재처리 대상이 아님)
    private static List<PaymentRecovery.PaidItem> toPaidItems(List<MarkPaidCommand.Item> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
            .filter(Objects::nonNull)
            .map(item -> new PaymentRecovery.PaidItem(item.orderItemId(), item.amount()))
            .toList();
    }

    private static MarkPaidCommand toCommand(PaymentRecovery recovery) {
        return new MarkPaidCommand(recovery.getOrderId(), recovery.getPaymentId(), recovery.getMemberId(),
            recovery.getAmount(), recovery.getItems().stream()
                .map(item -> new MarkPaidCommand.Item(item.orderItemId(), item.amount()))
                .toList());
    }
}
