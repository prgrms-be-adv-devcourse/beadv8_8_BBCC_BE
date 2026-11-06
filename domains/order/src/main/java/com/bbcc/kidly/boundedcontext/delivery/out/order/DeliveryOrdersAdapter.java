package com.bbcc.kidly.boundedcontext.delivery.out.order;

import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryOrders;
import com.bbcc.kidly.boundedcontext.order.app.port.in.DeliveryRecorder;
import com.bbcc.kidly.boundedcontext.order.app.port.in.DeliveryRecorder.HistoryActor;
import com.bbcc.kidly.boundedcontext.order.app.port.in.OrderLookup;
import com.bbcc.kidly.boundedcontext.order.app.port.in.OrderLookup.OrderView;
import com.bbcc.kidly.boundedcontext.order.app.port.in.OrderLookup.SellerOrderView;
import com.bbcc.kidly.boundedcontext.order.app.port.in.SellerItemCancellation;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 배송할 주문을 주문 컨텍스트의 주문 조회 포트로 읽고, 배송 결과를 배송 반영 포트로 남긴다(#641).
 * 판매자 발송 전 몫 취소는 판매자 취소 포트로 반영한다(#655).
 * 같은 앱 안의 동기 호출이라 부르는 쪽 트랜잭션에 합류한다.
 */
@Component
@RequiredArgsConstructor
public class DeliveryOrdersAdapter implements DeliveryOrders {

    private final OrderLookup orderLookup;
    private final DeliveryRecorder deliveryRecorder;
    private final SellerItemCancellation sellerItemCancellation;

    @Override
    public Optional<Order> findOrder(Long orderId) {
        return orderLookup.findOrder(orderId).map(DeliveryOrdersAdapter::toOrder);
    }

    // 판매 주문 목록은 자녀 칸을 읽지 않는 판매자용 조회로 읽는다(#997)
    @Override
    public List<Order> findOrdersWithItems(Collection<Long> orderIds) {
        return orderLookup.findOrdersForSeller(orderIds).stream().map(DeliveryOrdersAdapter::toOrder).toList();
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

    @Override
    public Optional<CancelledShare> cancelSellerItems(Long orderId, Long sellerId, String reason, String memo,
        Actor actor, LocalDateTime now) {
        return sellerItemCancellation.cancelSellerItems(orderId, sellerId, reason, memo,
                new HistoryActor(actor.type(), actor.id(), actor.source()), now)
            .map(share -> new CancelledShare(share.cancellationId(), share.orderItemIds(), share.orderCancelled()));
    }

    private static Order toOrder(SellerOrderView order) {
        OrderView.ShippingAddress address = order.shippingAddress();
        return new Order(order.orderId(), order.memberId(), order.orderedAt(),
            address == null ? null : new Order.ShippingAddress(address.recipientName(), address.phone(),
                address.zipCode(), address.address()),
            order.items().stream().map(item -> new Order.Item(item.orderItemId(), item.productId(), item.optionId(),
                item.sellerId(), item.productName(), item.optionName(), item.size(), item.unitPrice(), item.quantity(),
                item.amount(), item.status(), item.deliveredAt(), item.confirmedAt(), item.confirmType(),
                item.unavailableReason())).toList());
    }

    private static Order toOrder(OrderView order) {
        OrderView.ShippingAddress address = order.shippingAddress();
        return new Order(order.orderId(), order.memberId(), order.orderedAt(),
            address == null ? null : new Order.ShippingAddress(address.recipientName(), address.phone(),
                address.zipCode(), address.address()),
            order.items().stream().map(DeliveryOrdersAdapter::toItem).toList());
    }

    // 자녀 정보(OrderView.Item.child)는 옮기지 않는다(#997, 판매자 조회 경로에서 읽지 않음)
    private static Order.Item toItem(OrderView.Item item) {
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
            item.unavailableReason());
    }
}
