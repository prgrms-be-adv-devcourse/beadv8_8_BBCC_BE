package com.bbcc.kidly.boundedcontext.product;

import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.shared.order.event.OrderItemCancelledEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

final class OrderItemCancelledFixture {

    private static final long UNIT_AMOUNT = 10_000L;

    private OrderItemCancelledFixture() {
    }

    static OrderItemCancelledEvent orderItemCancelled(long orderId, long cancellationId,
        OrderItemCancelledEvent.Item... items) {
        return new OrderItemCancelledEvent(UUID.randomUUID(), LocalDateTime.now(),
            new OrderItemCancelledEvent.Data(orderId, OrderCreatedFixture.MEMBER_ID, ProductFixture.SELLER_ID,
                cancellationId, "OUT_OF_STOCK", List.of(items)));
    }

    static OrderItemCancelledEvent.Item cancelledOrderItem(long orderItemId, ProductOption option, int quantity) {
        return new OrderItemCancelledEvent.Item(orderItemId, option.getId(), quantity, UNIT_AMOUNT * quantity);
    }
}
