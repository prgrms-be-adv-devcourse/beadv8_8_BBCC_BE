package com.bbcc.kidly.boundedcontext.order.app.deadletter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class DeadLetterPayloadsTest {

    private final DeadLetterPayloads payloads = new DeadLetterPayloads(JsonMapper.builder().build());

    @Test
    @DisplayName("본문 공통 칸(producer · occurredAt · aggregateId · correlationId)과 결제 이벤트의 data.orderId를 꺼낸다")
    void readsCommonFieldsAndOrderId() {
        String body = """
            {"eventId":"e-1","eventType":"payment.completed","schemaVersion":1,"occurredAt":"2026-10-31T09:30:00",
             "producer":"payment","aggregateId":"500","correlationId":"42","data":{"paymentId":500,"orderId":42}}
            """;

        DeadLetterPayloads.Fields fields = payloads.read(body, "payment.completed", "42");

        assertThat(fields).isEqualTo(new DeadLetterPayloads.Fields(
            "payment", LocalDateTime.of(2026, 10, 31, 9, 30), "500", "42", 42L));
    }

    @Test
    @DisplayName("재고 이벤트도 주문 이벤트라 data.orderId를 관련 주문으로 쓴다")
    void stockEventHasOrderId() {
        String body = """
            {"producer":"product","aggregateId":"7","correlationId":"7","data":{"orderId":7}}
            """;

        assertThat(payloads.read(body, "stock.deducted", "7").orderId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("본문을 읽지 못하면 공통 칸은 빈칸, 주문 이벤트면 관련 주문 ID는 키(주문 ID)에서")
    void unreadableBodyFallsBackToKey() {
        DeadLetterPayloads.Fields fields = payloads.read("{not json", "stock.deduction-failed", "15");

        assertThat(fields).isEqualTo(new DeadLetterPayloads.Fields(null, null, null, null, 15L));
    }

    @Test
    @DisplayName("주문이 아닌 대상의 이벤트(상품 · 판매자 · 자녀)는 관련 주문 ID가 없다")
    void nonOrderEventHasNoOrderId() {
        String body = """
            {"producer":"product","aggregateId":"3","correlationId":"3","data":{"productId":3}}
            """;

        DeadLetterPayloads.Fields fields = payloads.read(body, "product.changed", "3");

        assertThat(fields.orderId()).isNull();
        assertThat(fields.producer()).isEqualTo("product");
    }

    @Test
    @DisplayName("종류를 모르거나(eventType 헤더 없음) 본문 · 키가 없거나 숫자가 아니면 빈칸")
    void missingValuesAreEmpty() {
        assertThat(payloads.read(null, null, null)).isEqualTo(new DeadLetterPayloads.Fields(null, null, null, null,
            null));
        assertThat(payloads.read("{\"occurredAt\":\"어제\",\"data\":{\"orderId\":\"x\"}}", "payment.failed", "abc"))
            .isEqualTo(new DeadLetterPayloads.Fields(null, null, null, null, null));
    }
}
