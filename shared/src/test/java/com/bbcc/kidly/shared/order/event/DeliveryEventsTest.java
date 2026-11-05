package com.bbcc.kidly.shared.order.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 배송 출발 · 완료 이벤트(#900): 이벤트 타입 · 발행 도메인 · 대상(배송 ID) · 흐름(키 = 주문 ID) · data 칸.
 */
class DeliveryEventsTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 5, 19, 0);

    @Test
    @DisplayName("delivery.shipped · delivery.delivered, 발행은 주문 도메인, 대상은 배송 ID, 키는 주문 ID")
    void metadata() {
        List<DomainEvent> events = List.of(
            new DeliveryShippedEvent(UUID.randomUUID(), AT, new DeliveryShippedEvent.Data(30L, 1024L, 1L, 2L, "CJ",
                "CJ대한통운", "1234567890", AT)),
            new DeliveryDeliveredEvent(UUID.randomUUID(), AT, new DeliveryDeliveredEvent.Data(30L, 1024L, 1L, 2L, "CJ",
                "CJ대한통운", "1234567890", AT)));

        assertThat(events).extracting(DomainEvent::eventType).containsExactly("delivery.shipped", "delivery.delivered");
        assertThat(events).extracting(DomainEvent::producer).containsOnly("order");
        assertThat(events).extracting(DomainEvent::aggregateId).containsOnly("30");
        assertThat(events).extracting(DomainEvent::correlationId).containsOnly("1024");
        assertThat(events).extracting(DomainEvent::schemaVersion).containsOnly(1);
    }

    @Test
    @DisplayName("칸: 배송 · 주문 · 구매자 · 판매자 · 택배사 코드 · 택배사 이름 · 운송장 번호 · 시각. 배송지 · 수령인 칸은 없다")
    void fields() {
        assertThat(names(DeliveryShippedEvent.Data.class)).containsExactly("deliveryId", "orderId", "memberId",
            "sellerId", "carrier", "carrierName", "trackingNumber", "shippedAt");
        assertThat(names(DeliveryDeliveredEvent.Data.class)).containsExactly("deliveryId", "orderId", "memberId",
            "sellerId", "carrier", "carrierName", "trackingNumber", "deliveredAt");
    }

    private static List<String> names(Class<?> record) {
        return Arrays.stream(record.getRecordComponents()).map(RecordComponent::getName).toList();
    }
}
