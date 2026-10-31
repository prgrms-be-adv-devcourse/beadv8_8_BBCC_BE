package com.bbcc.kidly.boundedcontext.order.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class OrderLogContextTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("요청 밖(traceId 없음): eventId · eventType · correlationId와 traceId = correlationId를 넣고, 닫으면 모두 지운다")
    void outsideRequest() {
        try (var ignored = OrderLogContext.open("e-1", "payment.completed", "42")) {
            assertThat(MDC.getCopyOfContextMap()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "eventId", "e-1", "eventType", "payment.completed", "correlationId", "42", "traceId", "42"));
        }

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    @DisplayName("HTTP 요청 안(traceId 있음): traceId는 그대로 두고, 닫은 뒤에도 요청 값이 남는다")
    void insideRequestKeepsTraceId() {
        MDC.put("traceId", "request-trace");
        MDC.put("memberId", "7");

        try (var ignored = OrderLogContext.open("e-1", "payment.completed", "42")) {
            assertThat(MDC.get("traceId")).isEqualTo("request-trace");
            assertThat(MDC.get("correlationId")).isEqualTo("42");
        }

        assertThat(MDC.getCopyOfContextMap()).containsExactlyInAnyOrderEntriesOf(Map.of(
            "traceId", "request-trace", "memberId", "7"));
    }

    @Test
    @DisplayName("바깥에 같은 키가 있으면 안에서는 새 값, 닫으면 바깥 값으로 되돌린다(겹쳐 열어도)")
    void restoresOuterValues() {
        try (var outer = OrderLogContext.open("outer", "stock.deducted", "1")) {
            try (var inner = OrderLogContext.open("inner", "payment.completed", "2")) {
                assertThat(MDC.get("eventId")).isEqualTo("inner");
                assertThat(MDC.get("correlationId")).isEqualTo("2");
                assertThat(MDC.get("traceId")).as("바깥이 채운 traceId는 그대로").isEqualTo("1");
            }
            assertThat(MDC.getCopyOfContextMap()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "eventId", "outer", "eventType", "stock.deducted", "correlationId", "1", "traceId", "1"));
        }

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    @DisplayName("없는 값(null)은 넣지 않고, 바깥 값이 있어도 이 범위에서는 가린다(다른 메시지 값으로 보이지 않게)")
    void nullValuesHideOuter() {
        try (var outer = OrderLogContext.open("outer", "stock.deducted", "1")) {
            try (var inner = OrderLogContext.open(null, null, null)) {
                assertThat(MDC.get("eventId")).isNull();
                assertThat(MDC.get("eventType")).isNull();
                assertThat(MDC.get("correlationId")).isNull();
                assertThat(MDC.get("traceId")).isEqualTo("1");
            }
            assertThat(MDC.get("eventId")).isEqualTo("outer");
        }
    }

    @Test
    @DisplayName("correlationId가 없고 traceId도 없으면 traceId를 만들지 않는다")
    void noCorrelationNoTrace() {
        try (var ignored = OrderLogContext.open("e-1", "product.changed", null)) {
            assertThat(MDC.getCopyOfContextMap()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "eventId", "e-1", "eventType", "product.changed"));
        }
    }

    @Test
    @DisplayName("처리가 예외로 끝나도 닫히며 지운다")
    void clearsOnException() {
        assertThatThrownBy(() -> {
            try (var ignored = OrderLogContext.open("e-1", "payment.completed", "42")) {
                throw new IllegalStateException("처리 실패");
            }
        }).isInstanceOf(IllegalStateException.class);

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}
