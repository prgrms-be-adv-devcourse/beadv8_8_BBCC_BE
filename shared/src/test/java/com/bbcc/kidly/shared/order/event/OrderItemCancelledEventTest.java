package com.bbcc.kidly.shared.order.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderItemCancelledEventTest {

    private static final LocalDateTime CANCELLED_AT = LocalDateTime.of(2026, 11, 2, 10, 0);

    @Test
    @DisplayName("이벤트 타입은 order-item.cancelled, 발행은 주문, 대상은 취소 ID, 흐름(키)은 주문 ID다")
    void metadata() {
        OrderItemCancelledEvent event = event();

        assertThat(event.eventType()).isEqualTo("order-item.cancelled");
        assertThat(event.producer()).isEqualTo("order");
        assertThat(event.aggregateId()).isEqualTo("90");
        assertThat(event.correlationId()).isEqualTo("7");
        assertThat(event.schemaVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("부분 환불 · 재고 복구에 필요한 값을 담는다: 주문 · 회원 · 판매자 · 취소 ID · 사유 · 주문상품(옵션 · 수량 · 금액)")
    void fields() {
        OrderItemCancelledEvent.Data data = event().data();

        assertThat(data.orderId()).isEqualTo(7L);
        assertThat(data.memberId()).isEqualTo(1L);
        assertThat(data.sellerId()).isEqualTo(2L);
        assertThat(data.cancellationId()).isEqualTo(90L);
        assertThat(data.reason()).isEqualTo("OUT_OF_STOCK");
        assertThat(data.items()).containsExactly(new OrderItemCancelledEvent.Item(70L, 3L, 2, 50_000L));
    }

    private static OrderItemCancelledEvent event() {
        return new OrderItemCancelledEvent(UUID.randomUUID(), CANCELLED_AT, new OrderItemCancelledEvent.Data(7L, 1L, 2L,
            90L, "OUT_OF_STOCK", List.of(new OrderItemCancelledEvent.Item(70L, 3L, 2, 50_000L))));
    }
}
