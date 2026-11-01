package com.bbcc.kidly.boundedcontext.delivery.out.order;

import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryOrders;
import com.bbcc.kidly.boundedcontext.order.app.port.in.DeliveryRecorder;
import com.bbcc.kidly.boundedcontext.order.app.port.in.DeliveryRecorder.HistoryActor;
import com.bbcc.kidly.boundedcontext.order.app.port.in.OrderLookup;
import com.bbcc.kidly.boundedcontext.order.app.port.in.OrderLookup.OrderView;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 배송할 주문을 주문 컨텍스트의 주문 조회 포트로 읽고, 배송 결과를 배송 반영 포트로 남긴다(#641).
 * 같은 앱 안의 동기 호출이라 부르는 쪽 트랜잭션에 합류한다.
 */
@Component
@RequiredArgsConstructor
public class DeliveryOrdersAdapter implements DeliveryOrders {

    private final OrderLookup orderLookup;
    private final DeliveryRecorder deliveryRecorder;

    @Override
    public Optional<Order> findOrder(Long orderId) {
        return orderLookup.findOrder(orderId).map(DeliveryOrdersAdapter::toOrder);
    }

    @Override
    public List<Order> findOrdersWithItems(Collection<Long> orderIds) {
        return orderLookup.findOrdersWithItems(orderIds).stream().map(DeliveryOrdersAdapter::toOrder).toList();
    }

    @Override
    public void recordDelivered(Long orderId, Long sellerId, LocalDateTime deliveredAt) {
        deliveryRecorder.recordDelivered(orderId, sellerId, deliveredAt);
    }

    @Override
    public void recordHistory(Long orderId, Long deliveryId, Actor actor, String from, String to, String reason,
        LocalDateTime occurredAt) {
        deliveryRecorder.recordDeliveryHistory(orderId, deliveryId,
            new HistoryActor(actor.type(), actor.id(), actor.source()), from, to, reason, occurredAt);
    }

    private static Order toOrder(OrderView order) {
        OrderView.ShippingAddress address = order.shippingAddress();
        return new Order(order.orderId(), order.orderedAt(),
            address == null ? null : new Order.ShippingAddress(address.recipientName(), address.phone(),
                address.zipCode(), address.address()),
            order.items().stream().map(DeliveryOrdersAdapter::toItem).toList());
    }

    private static Order.Item toItem(OrderView.Item item) {
        OrderView.Child child = item.child();
        return new Order.Item(
            item.orderItemId(),
            item.productId(),
            item.optionId(),
            item.sellerId(),
            item.productName(),
            item.optionName(),
            item.size(),
            item.unitPrice(),
            item.quantity(),
            item.amount(),
            item.status(),
            item.deliveredAt(),
            item.confirmedAt(),
            item.confirmType(),
            child == null ? null : new Order.Child(child.childId(), child.name(), child.heightCm(),
                child.weightKg(), child.bodyType()),
            item.unavailableReason());
    }
}
