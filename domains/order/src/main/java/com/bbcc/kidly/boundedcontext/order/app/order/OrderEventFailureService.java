package com.bbcc.kidly.boundedcontext.order.app.order;

import com.bbcc.kidly.boundedcontext.order.app.OrderActor;
import com.bbcc.kidly.boundedcontext.order.app.OrderErrorCode;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderEventFailureResult;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.StockDeductionFailedCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderEventFailureRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.recovery.OrderEventFailure;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 받은 이벤트(재고 차감됨 · 재고 차감 실패 · 결제 실패) 처리 실패 보관 · 관리자 재처리 · 종료(#449).
 * 결제 완료 반영 실패 보관(PaymentRecoveryService, #353)과 같은 모양이다. 종료는 기록만 닫고 주문은 바꾸지 않는다
 * (돈이 빠지지 않았으므로 남은 결제대기 주문은 결제 마감에 만료 취소된다).
 * 로그: 재처리 · 종료 결과마다 INFO 한 줄. 실패 보관은 리스너가 실패 로그 한 줄에 함께 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderEventFailureService {

    private final OrderEventFailureRepository failureRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;

    /**
     * stock.deducted(결제 요청) 처리 실패를 보관한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrderEventFailureResult recordStockDeductedFailure(Long orderId, RuntimeException error) {
        return record(OrderEventFailure.EventType.STOCK_DEDUCTED, orderId, List.of(), error);
    }

    /**
     * stock.deduction-failed(재고 부족 취소) 처리 실패를 보관한다. 주문상품별 사유는 재처리에 그대로 쓴다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrderEventFailureResult recordStockDeductionFailedFailure(StockDeductionFailedCommand command,
        RuntimeException error) {
        return record(OrderEventFailure.EventType.STOCK_DEDUCTION_FAILED, command.orderId(),
            command.items().stream()
                .map(item -> new OrderEventFailure.StockFailure(item.orderItemId(), item.reason()))
                .toList(),
            error);
    }

    /**
     * payment.failed(결제 실패 취소) 처리 실패를 보관한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrderEventFailureResult recordPaymentFailedFailure(Long orderId, RuntimeException error) {
        return record(OrderEventFailure.EventType.PAYMENT_FAILED, orderId, List.of(), error);
    }

    /**
     * 받은 이벤트 데이터와 실패 이유를 (주문, 종류)당 1건 보관한다(있으면 시도 +1 · 이유 갱신).
     * 리스너 트랜잭션이 되돌려져도 남도록 새 트랜잭션에서 저장한다(호출한 public 메서드). 그 트랜잭션이 주문 행을 바꾼 채
     * 열려 있으므로 여기서는 주문을 읽지도 잠그지도 않는다(기록 테이블만 쓴다). 기존 기록은 기다리지 않는 잠금으로 읽는다:
     * 재처리 · 종료가 그 기록을 잠근 채 이 주문 행을 기다리면 같은 스레드의 리스너 트랜잭션과 순환 대기가 되므로 바로 실패한다.
     */
    private OrderEventFailureResult record(OrderEventFailure.EventType eventType, Long orderId,
        List<OrderEventFailure.StockFailure> stockFailures, RuntimeException error) {
        LocalDateTime now = LocalDateTime.now();
        String failureCode = PaymentFailures.failureCode(error);
        Optional<OrderEventFailure> existing = failureRepository.findByOrderIdAndEventTypeForUpdateNoWait(orderId,
            eventType);
        OrderEventFailure failure = existing
            .map(found -> {
                found.recordFailure(failureCode, now);
                return found;
            })
            .orElseGet(() -> failureRepository.save(
                OrderEventFailure.open(eventType, orderId, stockFailures, failureCode, now)));
        return OrderEventFailureResult.from(failure);
    }

    /**
     * 최근 기록부터. statusName이 null이면 모든 상태.
     */
    @Transactional(readOnly = true)
    public Page<OrderEventFailureResult> getFailures(String statusName, int page, int size) {
        OrderEventFailure.Status status = toStatus(statusName);
        PageRequest pageable = PageRequest.of(page, size);
        // 저장소는 offset(page × size)이 int를 넘으면 조회하지 못하므로(500), 조회 없이 빈 페이지로 답한다
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            long total = status == null ? failureRepository.count() : failureRepository.countByStatus(status);
            return new PageImpl<>(List.of(), pageable, total);
        }
        Page<OrderEventFailure> failures = status == null
            ? failureRepository.findAllByOrderByIdDesc(pageable)
            : failureRepository.findAllByStatusOrderByIdDesc(status, pageable);
        return failures.map(OrderEventFailureResult::from);
    }

    @Transactional(readOnly = true)
    public OrderEventFailureResult getFailure(Long failureId) {
        return OrderEventFailureResult.from(find(failureId));
    }

    /**
     * 보관한 이벤트 데이터로 그 이벤트 처리를 한 트랜잭션으로 다시 실행한다(결제 요청 · 재고 부족 취소 · 결제 실패 취소).
     * 실패하면 이 트랜잭션은 전부 되돌려지고, OrderEventFailureRetry가 새 트랜잭션에서 시도 횟수를 센다.
     * 주문이 이미 결제대기가 아니면(만료 취소 · 다른 이유로 끝남) 처리하지 않고 기록만 CANCELLED로 닫는다(주문 상태는 그대로).
     * 이미 복구됐으면 그대로 돌려주고, 종료됐으면 409.
     */
    @Transactional
    public OrderEventFailureResult recover(Long failureId, Long adminId) {
        OrderEventFailure failure = findForUpdate(failureId);
        if (failure.getStatus() == OrderEventFailure.Status.RECOVERED) {
            return OrderEventFailureResult.from(failure);
        }
        if (failure.getStatus() == OrderEventFailure.Status.CANCELLED) {
            throw new BusinessException(OrderErrorCode.EVENT_FAILURE_CLOSED);
        }
        boolean pendingPayment = orderStatusOf(failure) == Order.Status.PENDING_PAYMENT;
        if (pendingPayment) {
            apply(failure, OrderActor.admin(adminId, OrderActor.EVENT_FAILURE_RETRY));
        }
        LocalDateTime now = LocalDateTime.now();
        if (pendingPayment) {
            failure.recover(now);
        } else {
            failure.cancel(now);
        }
        log.info("이벤트 처리 재처리: failureId={} eventType={} orderId={} result={}",
            failure.getId(), failure.getEventType(), failure.getOrderId(), failure.getStatus());
        return OrderEventFailureResult.from(failure);
    }

    /**
     * 재처리가 다시 실패했다. 아직 종료되지 않은 기록만 시도 +1 · 이유를 바꾼다(동시에 다른 재처리가 닫았으면 그대로 둠).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrderEventFailureResult recordRetryFailure(Long failureId, RuntimeException error) {
        OrderEventFailure failure = find(failureId);
        if (failure.isClosed()) {
            return OrderEventFailureResult.from(failure);
        }
        // 재처리가 기록을 잡은 사이 다른 경로로 주문이 취소됐으면(주문 취소는 잡힌 기록을 건너뜀, #598) 재처리할 것이 없으므로 닫는다
        if (isOrderCancelled(failure.getOrderId())) {
            failure.closeForOrderCancelled(LocalDateTime.now());
        } else {
            failure.recordFailure(PaymentFailures.failureCode(error), LocalDateTime.now());
        }
        log.info("이벤트 처리 재처리: failureId={} eventType={} orderId={} result={} attemptCount={} failureCode={}",
            failure.getId(), failure.getEventType(), failure.getOrderId(), failure.getStatus(),
            failure.getAttemptCount(), failure.getFailureCode());
        return OrderEventFailureResult.from(failure);
    }

    /**
     * 기록만 CANCELLED로 닫는다(주문은 바꾸지 않음). 이미 종료됐으면 그대로 돌려주고, 이미 복구됐으면 409.
     */
    @Transactional
    public OrderEventFailureResult cancel(Long failureId) {
        OrderEventFailure failure = findForUpdate(failureId);
        if (failure.getStatus() == OrderEventFailure.Status.CANCELLED) {
            return OrderEventFailureResult.from(failure);
        }
        if (failure.getStatus() == OrderEventFailure.Status.RECOVERED) {
            throw new BusinessException(OrderErrorCode.EVENT_FAILURE_CLOSED);
        }
        failure.cancel(LocalDateTime.now());
        log.info("이벤트 처리 실패 종료: failureId={} eventType={} orderId={} result={}",
            failure.getId(), failure.getEventType(), failure.getOrderId(), failure.getStatus());
        return OrderEventFailureResult.from(failure);
    }

    private void apply(OrderEventFailure failure, OrderActor admin) {
        Long orderId = failure.getOrderId();
        switch (failure.getEventType()) {
            case STOCK_DEDUCTED -> orderService.requestPayment(orderId);
            case STOCK_DEDUCTION_FAILED -> orderService.cancelForStockDeductionFailure(
                new StockDeductionFailedCommand(orderId, failure.getStockFailures().stream()
                    .map(item -> new StockDeductionFailedCommand.Item(item.orderItemId(), item.reason()))
                    .toList()),
                admin, WaitingRecord.eventFailure(failure.getId()));
            case PAYMENT_FAILED -> orderService.cancelForPaymentFailure(orderId, admin,
                WaitingRecord.eventFailure(failure.getId()));
        }
    }

    // in은 도메인 enum을 쓰지 않으므로 상태 이름은 여기서 바꾼다. 이름이 정확히 같아야 한다(소문자 · 모르는 값은 400)
    private static OrderEventFailure.Status toStatus(String statusName) {
        if (statusName == null) {
            return null;
        }
        try {
            return OrderEventFailure.Status.valueOf(statusName);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(OrderErrorCode.INVALID_EVENT_FAILURE_STATUS);
        }
    }

    private OrderEventFailure find(Long failureId) {
        return failureRepository.findById(failureId)
            .orElseThrow(() -> new BusinessException(OrderErrorCode.EVENT_FAILURE_NOT_FOUND));
    }

    /**
     * 재처리 · 종료는 맨 처음에 기록 행을 잠가 읽는다(결제 복구와 같은 순서: 기록 → 주문). 동시에 오면 차례로 실행되고,
     * 늦은 쪽은 닫힌 기록을 본다(재처리 두 번 → 한 번만 처리, 재처리와 종료 → 한쪽만 반영).
     */
    private OrderEventFailure findForUpdate(Long failureId) {
        return failureRepository.findByIdForUpdate(failureId)
            .orElseThrow(() -> new BusinessException(OrderErrorCode.EVENT_FAILURE_NOT_FOUND));
    }

    // 기록은 주문이 있을 때만 생기고 주문은 지우지 않는다. 그래도 없으면 불변식 위반이라 예외로 알린다
    private boolean isOrderCancelled(Long orderId) {
        return orderRepository.findById(orderId).map(order -> order.getStatus() == Order.Status.CANCELLED)
            .orElse(false);
    }

    private Order.Status orderStatusOf(OrderEventFailure failure) {
        return orderRepository.findById(failure.getOrderId())
            .map(Order::getStatus)
            .orElseThrow(() -> new IllegalStateException("기록의 주문이 없습니다: orderId=" + failure.getOrderId()));
    }
}
