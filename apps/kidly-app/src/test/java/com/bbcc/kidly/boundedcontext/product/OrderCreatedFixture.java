package com.bbcc.kidly.boundedcontext.product;

import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.shared.order.event.OrderCreatedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 재고 차감 테스트가 받을 주문 생성됨을 만든다. */
final class OrderCreatedFixture {

    static final long MEMBER_ID = 1L;

    private OrderCreatedFixture() {
    }

    static OrderCreatedEvent orderCreated(long orderId, OrderCreatedEvent.Item... items) {
        return orderCreated(orderId, List.of(items));
    }

    static OrderCreatedEvent orderCreated(long orderId, List<OrderCreatedEvent.Item> items) {
        return new OrderCreatedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new OrderCreatedEvent.Data(orderId, MEMBER_ID, items));
    }

    static OrderCreatedEvent.Item orderItem(long orderItemId, ProductOption option, int quantity) {
        return new OrderCreatedEvent.Item(orderItemId, option.getId(), quantity);
    }
}
