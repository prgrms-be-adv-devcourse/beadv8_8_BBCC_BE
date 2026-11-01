package com.bbcc.kidly.boundedcontext.order.out.delivery;

import com.bbcc.kidly.boundedcontext.delivery.app.port.in.DeliveryOperations;
import com.bbcc.kidly.boundedcontext.delivery.app.port.in.DeliveryOperations.DeliveryActor;
import com.bbcc.kidly.boundedcontext.delivery.app.port.in.DeliveryOperations.DeliveryView;
import com.bbcc.kidly.boundedcontext.delivery.app.port.in.ShippingDelays;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeliveries;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * 주문의 배송을 배송 컨텍스트의 들어오는 포트로 다룬다(#641). 같은 앱 안의 동기 호출이라 부르는 쪽 트랜잭션에 합류한다.
 */
@Component
@RequiredArgsConstructor
public class OrderDeliveriesAdapter implements OrderDeliveries {

    private final DeliveryOperations deliveryOperations;
    private final ShippingDelays shippingDelays;

    @Override
    public void createFor(Long orderId) {
        deliveryOperations.createFor(orderId);
    }

    @Override
    public void createFor(Long orderId, Actor actor) {
        deliveryOperations.createFor(orderId, new DeliveryActor(actor.type(), actor.id(), actor.source()));
    }

    @Override
    public List<Delivery> findByOrder(Long orderId) {
        return deliveryOperations.findByOrder(orderId).stream().map(OrderDeliveriesAdapter::toDelivery).toList();
    }

    @Override
    public List<CancelledDelivery> cancelByOrder(Long orderId) {
        return deliveryOperations.cancelByOrder(orderId).stream()
            .map(change -> new CancelledDelivery(change.from(), toDelivery(change.delivery())))
            .toList();
    }

    @Override
    public Page<DelayedDelivery> findShippingDelayed(LocalDateTime before, Pageable pageable) {
        return shippingDelays.findShippingDelayed(before, pageable)
            .map(view -> new DelayedDelivery(view.orderId(), view.deliveryId(), view.createdAt()));
    }

    @Override
    public long countShippingDelayed(LocalDateTime before) {
        return shippingDelays.countShippingDelayed(before);
    }

    private static Delivery toDelivery(DeliveryView view) {
        return new Delivery(view.deliveryId(), view.orderId(), view.sellerId(), view.status(), view.carrier(),
            view.trackingNumber(), view.shippedAt(), view.deliveredAt(), view.shipped());
    }
}
