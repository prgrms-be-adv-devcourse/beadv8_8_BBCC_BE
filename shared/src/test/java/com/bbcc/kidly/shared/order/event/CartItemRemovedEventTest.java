package com.bbcc.kidly.shared.order.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CartItemRemovedEventTest {

    private static final LocalDateTime REMOVED_AT = LocalDateTime.of(2026, 11, 2, 16, 0);

    @Test
    @DisplayName("이벤트 타입은 cart.item-removed, 발행은 주문 도메인, 대상은 장바구니 줄 ID, 흐름(키)은 회원 ID다(담기와 같은 키)")
    void metadata() {
        CartItemRemovedEvent event = new CartItemRemovedEvent(UUID.randomUUID(), REMOVED_AT,
            new CartItemRemovedEvent.Data(40L, 1L, null, 10L, 3L, 5));

        assertThat(event.eventType()).isEqualTo("cart.item-removed");
        assertThat(event.producer()).isEqualTo("order");
        assertThat(event.aggregateId()).isEqualTo("40");
        assertThat(event.correlationId()).isEqualTo("1");
        assertThat(event.schemaVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("뺐다는 사실만 담는다: 줄 · 회원 · 자녀(없으면 null) · 상품 · 옵션 · 뺄 때 줄 수량")
    void fields() {
        assertThat(Arrays.stream(CartItemRemovedEvent.Data.class.getRecordComponents()).map(RecordComponent::getName))
            .containsExactly("cartItemId", "memberId", "childId", "productId", "optionId", "quantity");
    }
}
