package com.bbcc.kidly.shared.order.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderPaidEventTest {

    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 11, 5, 18, 0);

    @Test
    @DisplayName("이벤트 타입은 클래스 이름 규칙 그대로 order.paid, 발행은 주문 도메인, 대상 · 흐름(키)은 주문 ID다")
    void metadata() {
        OrderPaidEvent event = new OrderPaidEvent(UUID.randomUUID(), PAID_AT, new OrderPaidEvent.Data(1024L, 1L,
            PAID_AT, 25_000L, List.of(new OrderPaidEvent.Item(10L, 2L, 3L, "상품 A", null, 1, 25_000L))));

        assertThat(event.eventType()).isEqualTo("order.paid");
        assertThat(event.producer()).isEqualTo("order");
        assertThat(event.aggregateId()).isEqualTo("1024");
        assertThat(event.correlationId()).isEqualTo("1024");
        assertThat(event.schemaVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("칸: 주문 · 회원 · 결제 시각 · 총액 · 주문상품(스냅샷 판매자 · 상품 · 상품명 · 옵션명 · 수량 · 금액). 배송지 · 자녀 칸은 없다")
    void fields() {
        assertThat(names(OrderPaidEvent.Data.class))
            .containsExactly("orderId", "memberId", "paidAt", "totalAmount", "items");
        assertThat(names(OrderPaidEvent.Item.class)).containsExactly(
            "orderItemId", "sellerId", "productId", "productName", "optionName", "quantity", "amount");
    }

    private static List<String> names(Class<?> record) {
        return Arrays.stream(record.getRecordComponents()).map(RecordComponent::getName).toList();
    }
}
