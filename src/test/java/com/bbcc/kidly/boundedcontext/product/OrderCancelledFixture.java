package com.bbcc.kidly.boundedcontext.product;

import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 재고 복구 테스트가 받을 주문 취소됨을 만든다. 주문상품은 주문 생성됨과 같은 값으로 만든다. */
final class OrderCancelledFixture {

    private OrderCancelledFixture() {
    }

    // 결제 실패로 취소된 주문이다
    static OrderCancelledEvent orderCancelled(long orderId, OrderCancelledEvent.Item... items) {
        return new OrderCancelledEvent(UUID.randomUUID(), LocalDateTime.now(),
            new OrderCancelledEvent.Data(orderId, OrderCreatedFixture.MEMBER_ID, "PAYMENT_FAILED", List.of(items)));
    }

    static OrderCancelledEvent.Item cancelledItem(long orderItemId, ProductOption option, int quantity) {
        return new OrderCancelledEvent.Item(orderItemId, option.getId(), quantity);
    }
}
