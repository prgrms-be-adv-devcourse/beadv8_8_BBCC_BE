package com.bbcc.kidly.boundedcontext.product;

import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

final class OrderCancelledFixture {

    private OrderCancelledFixture() {
    }

    static OrderCancelledEvent orderCancelled(long orderId, OrderCancelledEvent.Item... items) {
        return new OrderCancelledEvent(UUID.randomUUID(), LocalDateTime.now(),
            new OrderCancelledEvent.Data(orderId, OrderCreatedFixture.MEMBER_ID, "PAYMENT_FAILED", List.of(items)));
    }

    static OrderCancelledEvent.Item cancelledItem(long orderItemId, ProductOption option, int quantity) {
        return new OrderCancelledEvent.Item(orderItemId, option.getId(), quantity);
    }
}
