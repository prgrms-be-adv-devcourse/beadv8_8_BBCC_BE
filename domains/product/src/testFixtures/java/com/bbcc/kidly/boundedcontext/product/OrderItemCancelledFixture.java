package com.bbcc.kidly.boundedcontext.product;

import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.shared.order.event.OrderItemCancelledEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 재고 복구 테스트가 받을 판매자 부분 취소(주문상품 취소됨)를 만든다. 주문상품은 주문 생성됨과 같은 값으로 만든다. */
final class OrderItemCancelledFixture {

    private static final long UNIT_AMOUNT = 10_000L;

    private OrderItemCancelledFixture() {
    }

    // 재고가 없어 판매자가 발송 전에 자기 몫을 취소했다
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
