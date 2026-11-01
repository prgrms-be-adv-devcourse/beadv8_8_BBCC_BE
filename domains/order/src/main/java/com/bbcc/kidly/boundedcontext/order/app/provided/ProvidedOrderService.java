package com.bbcc.kidly.boundedcontext.order.app.provided;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderStatusHistoryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.in.DeliveryRecorder;
import com.bbcc.kidly.boundedcontext.order.app.port.in.OrderLookup;
import com.bbcc.kidly.boundedcontext.order.app.port.in.OrderLookup.OrderView;
import com.bbcc.kidly.boundedcontext.order.domain.history.OrderStatusHistory;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 조회 · 배송 반영을 다른 컨텍스트에 내준다(#617 · #641, {@link OrderLookup} · {@link DeliveryRecorder}). 부르는 쪽 트랜잭션에 합류한다(REQUIRED).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ProvidedOrderService implements OrderLookup, DeliveryRecorder {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;

    @Override
    public Optional<OrderView> findOrder(Long orderId) {
        return orderRepository.findById(orderId).map(ProvidedOrderService::toView);
    }

    @Override
    public List<OrderView> findOrdersWithItems(Collection<Long> orderIds) {
        return orderRepository.findAllWithItemsByIdIn(orderIds).stream().map(ProvidedOrderService::toView).toList();
    }

    @Override
    public void recordDelivered(Long orderId, Long sellerId, LocalDateTime deliveredAt) {
        orderRepository.findById(orderId).ifPresent(order -> order.recordDelivered(sellerId, deliveredAt));
    }

    @Override
    public void recordDeliveryHistory(Long orderId, Long deliveryId, HistoryActor actor, String from, String to,
        String reason, LocalDateTime occurredAt) {
        historyRepository.save(OrderStatusHistory.of(orderId, OrderStatusHistory.TargetType.DELIVERY, deliveryId, from,
            to, reason, OrderStatusHistory.ActorType.valueOf(actor.type()), actor.id(), actor.source(), occurredAt));
    }

    private static OrderView toView(Order order) {
        Order.ShippingAddress address = order.getShippingAddress();
        return new OrderView(order.getId(), order.getMemberId(), order.getCreatedAt(),
            address == null ? null : new OrderView.ShippingAddress(address.recipientName(), address.phone(),
                address.zipCode(), address.address()),
            order.getItems().stream().map(ProvidedOrderService::toItem).toList());
    }

    private static OrderView.Item toItem(OrderItem item) {
        OrderItem.ProductSnapshot product = item.getProduct();
        OrderItem.ChildSnapshot child = item.getChild();
        return new OrderView.Item(
            item.getId(),
            product.productId(),
            product.optionId(),
            product.sellerId(),
            product.productName(),
            product.optionName(),
            product.size(),
            product.unitPrice(),
            item.getQuantity(),
            item.getAmount(),
            item.getStatus().name(),
            item.getDeliveredAt(),
            item.getConfirmedAt(),
            item.getConfirmType() == null ? null : item.getConfirmType().name(),
            child == null ? null : new OrderView.Child(child.childId(), child.name(), child.heightCm(),
                child.weightKg(), child.bodyType()),
            item.getUnavailableReason());
    }
}
