package com.bbcc.kidly.shared.order.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderItemPurchaseConfirmedEventTest {

    private static final LocalDateTime CONFIRMED_AT = LocalDateTime.of(2026, 10, 30, 9, 0);

    @Test
    @DisplayName("이벤트 타입은 order-item.purchase-confirmed, 대상은 주문상품 ID, 흐름은 주문 ID다")
    void metadata() {
        OrderItemPurchaseConfirmedEvent event = event(1L);

        assertThat(event.eventType()).isEqualTo("order-item.purchase-confirmed");
        assertThat(event.producer()).isEqualTo("order");
        assertThat(event.aggregateId()).isEqualTo("70");
        assertThat(event.correlationId()).isEqualTo("7");
    }

    @Test
    @DisplayName("기획서 8장 계약 필드를 담는다. 자녀를 고르지 않았으면 childId는 null")
    void fields() {
        OrderItemPurchaseConfirmedEvent.Data data = event(null).data();

        assertThat(data.orderItemId()).isEqualTo(70L);
        assertThat(data.orderId()).isEqualTo(7L);
        assertThat(data.memberId()).isEqualTo(1L);
        assertThat(data.sellerId()).isEqualTo(2L);
        assertThat(data.childId()).isNull();
        assertThat(data.productId()).isEqualTo(10L);
        assertThat(data.optionId()).isEqualTo(3L);
        assertThat(data.size()).isEqualTo("110");
        assertThat(data.quantity()).isEqualTo(2);
        assertThat(data.amount()).isEqualTo(50_000L);
        assertThat(data.confirmedAt()).isEqualTo(CONFIRMED_AT);
    }

    private static OrderItemPurchaseConfirmedEvent event(Long childId) {
        return new OrderItemPurchaseConfirmedEvent(UUID.randomUUID(), CONFIRMED_AT,
            new OrderItemPurchaseConfirmedEvent.Data(70L, 7L, 1L, 2L, childId, 10L, 3L, "110", 2, 50_000L,
                CONFIRMED_AT));
    }
}
