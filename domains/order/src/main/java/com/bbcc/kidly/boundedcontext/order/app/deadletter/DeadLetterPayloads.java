package com.bbcc.kidly.boundedcontext.order.app.deadletter;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * DLT 메시지 본문에서 사람이 볼 칸을 꺼낸다(#601). 계약과 다른 본문도 저장해야 하므로 실패하지 않는다: 읽지 못한 칸은 null.
 * 관련 주문 ID는 주문을 다루는 이벤트(결제 완료 · 결제 실패 · 재고 차감됨 · 재고 차감 실패)만 data.orderId에서, 없으면 키에서
 * (이 이벤트들의 키 = correlationId = 주문 ID) 꺼낸다. 상품 · 판매자 · 자녀 이벤트는 주문이 없다.
 */
@Component
class DeadLetterPayloads {

    private static final Set<String> ORDER_EVENT_TYPES = Set.of("payment.completed", "payment.failed",
        "stock.deducted", "stock.deduction-failed");

    private final JsonMapper jsonMapper;

    DeadLetterPayloads(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    record Fields(String producer, LocalDateTime occurredAt, String aggregateId, String correlationId, Long orderId) {
    }

    Fields read(String payload, String eventType, String key) {
        JsonNode body = tree(payload);
        Long orderId = null;
        // Set.of는 null을 물으면 예외라 종류를 모르는 경우(eventType 헤더 없음)를 먼저 거른다
        if (eventType != null && ORDER_EVENT_TYPES.contains(eventType)) {
            orderId = longOf(body.path("data").path("orderId"));
            if (orderId == null) {
                orderId = parseLong(key);
            }
        }
        return new Fields(text(body, "producer"), dateTime(text(body, "occurredAt")), text(body, "aggregateId"),
            text(body, "correlationId"), orderId);
    }

    // 읽지 못하면 빈 노드(모든 칸이 없음)
    private JsonNode tree(String payload) {
        if (payload == null) {
            return jsonMapper.missingNode();
        }
        try {
            return jsonMapper.readTree(payload);
        } catch (JacksonException e) {
            return jsonMapper.missingNode();
        }
    }

    private static String text(JsonNode body, String field) {
        JsonNode node = body.path(field);
        return node.isString() ? node.asString() : null;
    }

    private static Long longOf(JsonNode node) {
        return node.isIntegralNumber() && node.canConvertToLong() ? node.asLong() : null;
    }

    private static Long parseLong(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDateTime dateTime(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
