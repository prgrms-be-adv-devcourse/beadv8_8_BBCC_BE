package com.bbcc.kidly.shared.order.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CartItemPurchasedEventTest {

    private static final LocalDateTime PURCHASED_AT = LocalDateTime.of(2026, 11, 6, 16, 0);

    @Test
    @DisplayName("이벤트 타입은 cart.item-purchased, 발행은 주문 도메인, 대상은 장바구니 줄 ID, 흐름(키)은 회원 ID다(담기 · 빼기와 같은 키)")
    void metadata() {
        CartItemPurchasedEvent event = new CartItemPurchasedEvent(UUID.randomUUID(), PURCHASED_AT,
            new CartItemPurchasedEvent.Data(40L, "CHILD", 5L, 1L, 3L, 5L, 2, 7L, 70L, "wish-1"));

        assertThat(event.eventType()).isEqualTo("cart.item-purchased");
        assertThat(event.producer()).isEqualTo("order");
        assertThat(event.aggregateId()).isEqualTo("40");
        assertThat(event.correlationId()).isEqualTo("1");
        assertThat(event.schemaVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("산 줄이 정리됐다는 사실만 담는다: 줄 · 주인 · 결제 회원 · 옵션 · 자녀 · 수량 · 주문 · 주문상품 · 예약")
    void fields() {
        assertThat(Arrays.stream(CartItemPurchasedEvent.Data.class.getRecordComponents())
            .map(RecordComponent::getName))
            .containsExactly("cartItemId", "ownerType", "ownerId", "memberId", "optionId", "childId", "quantity",
                "orderId", "orderItemId", "reservationId");
    }
}
