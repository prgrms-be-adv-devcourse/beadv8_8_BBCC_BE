package com.bbcc.kidly.shared.order.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CartItemAddedEventTest {

    private static final LocalDateTime ADDED_AT = LocalDateTime.of(2026, 11, 1, 23, 0);

    @Test
    @DisplayName("이벤트 타입은 cart.item-added, 발행은 주문 도메인, 대상은 장바구니 줄 ID, 흐름(키)은 회원 ID다")
    void metadata() {
        CartItemAddedEvent event = event(1L);

        assertThat(event.eventType()).isEqualTo("cart.item-added");
        assertThat(event.producer()).isEqualTo("order");
        assertThat(event.aggregateId()).isEqualTo("40");
        assertThat(event.correlationId()).isEqualTo("1");
        assertThat(event.schemaVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("담았다는 사실만 담는다: 줄 · 회원 · 자녀(없으면 null) · 상품 · 옵션 · 이번 수량 · 담은 뒤 줄 수량")
    void fields() {
        CartItemAddedEvent.Data data = event(null).data();

        assertThat(data.cartItemId()).isEqualTo(40L);
        assertThat(data.memberId()).isEqualTo(1L);
        assertThat(data.childId()).isNull();
        assertThat(data.productId()).isEqualTo(10L);
        assertThat(data.optionId()).isEqualTo(3L);
        assertThat(data.quantity()).isEqualTo(2);
        assertThat(data.cartQuantity()).isEqualTo(5);
    }

    private static CartItemAddedEvent event(Long childId) {
        return new CartItemAddedEvent(UUID.randomUUID(), ADDED_AT,
            new CartItemAddedEvent.Data(40L, 1L, childId, 10L, 3L, 2, 5));
    }
}
