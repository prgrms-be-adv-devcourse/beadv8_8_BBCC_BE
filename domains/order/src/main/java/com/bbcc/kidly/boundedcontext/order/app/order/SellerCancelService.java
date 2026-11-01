package com.bbcc.kidly.boundedcontext.order.app.order;

import com.bbcc.kidly.boundedcontext.order.app.OrderActor;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderEventOutbox;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderItemCancellationRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderStatusHistoryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.in.DeliveryRecorder.HistoryActor;
import com.bbcc.kidly.boundedcontext.order.app.port.in.SellerItemCancellation;
import com.bbcc.kidly.boundedcontext.order.domain.history.OrderStatusHistory;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItemCancellation;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.order.event.OrderItemCancelledEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매자 발송 전 몫 취소를 주문에 반영한다(#655, {@link SellerItemCancellation}). 무엇을 취소할지는 Order가 판단한다.
 * 다른 들어오는 포트 구현(app.provided)과 달리 여기 둔다: 주문이 취소되면 재처리 대기 기록을 닫는 {@link WaitingRecordCloser}가
 * 주문 취소 기능(app.order) 안에 있기 때문이다.
 * <ul>
 *   <li>주문을 FOR UPDATE로 읽는다: 두 판매자가 남은 몫을 동시에 취소하면 뒤쪽이 앞쪽 커밋을 기다렸다가 그 결과를 보고
 *       마지막 몫이면 주문을 취소한다(둘 다 성공, 주문 취소 · 주문 이력은 한 번). 주문 행 → 배송 행 순서라 구매자 취소와 순서가 같다</li>
 *   <li>이벤트는 주문 이벤트와 같이 Spring 이벤트 발행 + 같은 트랜잭션의 아웃박스 저장. 주문이 취소돼도 order.cancelled는 보내지 않는다</li>
 *   <li>배송 취소와 한 트랜잭션이어야 하므로 부르는 쪽 트랜잭션에 합류만 한다</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SellerCancelService implements SellerItemCancellation {

    private final OrderRepository orderRepository;
    private final OrderItemCancellationRepository cancellationRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final EventPublisher eventPublisher;
    private final OrderEventOutbox outbox;
    private final WaitingRecordCloser waitingRecordCloser;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<CancelledShare> cancelSellerItems(Long orderId, Long sellerId, String reason, String memo,
        HistoryActor actor, LocalDateTime now) {
        OrderItem.CancelReason cancelReason = OrderItem.CancelReason.valueOf(reason);
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null) {
            return Optional.empty();
        }
        Order.Status from = order.getStatus();
        OrderItemCancellation cancellation = cancellationRepository.save(
            OrderItemCancellation.create(orderId, sellerId, cancelReason, memo));
        List<OrderItem> cancelled = order.cancelSellerItems(sellerId, cancelReason, cancellation.getId(), now);
        if (cancelled.isEmpty()) {
            // 저장한 취소 기록은 부르는 쪽이 실패로 끝내며 함께 롤백된다
            return Optional.empty();
        }
        OrderActor orderActor = new OrderActor(OrderStatusHistory.ActorType.valueOf(actor.type()), actor.id(),
            actor.source());
        for (OrderItem item : cancelled) {
            historyRepository.save(orderActor.history(orderId, OrderStatusHistory.TargetType.ORDER_ITEM, item.getId(),
                OrderItem.Status.ORDERED, item.getStatus(), reason, now));
        }
        boolean orderCancelled = order.getStatus() == Order.Status.CANCELLED;
        WaitingRecordCloser.Closed closed = null;
        if (orderCancelled) {
            historyRepository.save(orderActor.history(orderId, OrderStatusHistory.TargetType.ORDER, orderId, from,
                order.getStatus(), order.getCancelReason().name(), now));
            closed = waitingRecordCloser.closeFor(orderId, orderActor, null, now);
        }
        OrderItemCancelledEvent event = new OrderItemCancelledEvent(UUID.randomUUID(), now,
            new OrderItemCancelledEvent.Data(orderId, order.getMemberId(), sellerId, cancellation.getId(), reason,
                cancelled.stream()
                    .map(item -> new OrderItemCancelledEvent.Item(item.getId(), item.getProduct().optionId(),
                        item.getQuantity(), item.getAmount()))
                    .toList()));
        eventPublisher.publish(event);
        outbox.append(event);
        List<Long> orderItemIds = cancelled.stream().map(OrderItem::getId).toList();
        // 결과 하나에 한 줄: 주문이 취소돼 닫은 대기 기록이 있으면 그 ID를 같은 줄에 붙인다
        if (closed == null || closed.isEmpty()) {
            log.info("판매자 취소: orderId={} sellerId={} cancellationId={} orderItemIds={} reason={} orderCancelled={}",
                orderId, sellerId, cancellation.getId(), orderItemIds, reason, orderCancelled);
        } else {
            log.info("판매자 취소: orderId={} sellerId={} cancellationId={} orderItemIds={} reason={} orderCancelled={} "
                    + "closedEventFailureIds={} closedPaymentRecoveryId={}", orderId, sellerId, cancellation.getId(),
                orderItemIds, reason, orderCancelled, closed.eventFailureIds(), closed.paymentRecoveryId());
        }
        return Optional.of(new CancelledShare(cancellation.getId(), orderItemIds, orderCancelled));
    }
}
