package com.bbcc.kidly.boundedcontext.review.in.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 자유 문구가 될 수 있는 칸은 정해진 형식일 때만 내보내고, 아니면 null.
 */
class McpSafeTextTest {

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"kidly.order.events", "kidly.order.events.community.dlt", "kidly.member.events"})
    @DisplayName("토픽 이름 형식이면 그대로")
    void topics(String value) {
        assertThat(McpSafeText.topic(value)).isEqualTo(value);
    }

    @ParameterizedTest(name = "[{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"kidly order", "토픽", "kidly.order.events\nsecret", "a/b"})
    @DisplayName("토픽 형식이 아니면 null")
    void notTopics(String value) {
        assertThat(McpSafeText.topic(value)).isNull();
    }

    @Test
    @DisplayName("너무 긴 토픽 · eventType은 null")
    void tooLong() {
        assertThat(McpSafeText.topic("x".repeat(250))).isNull();
        assertThat(McpSafeText.eventType("a." + "b".repeat(120))).isNull();
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"order-item.purchase-confirmed", "product.changed", "member.withdrawn"})
    @DisplayName("eventType 형식(소문자 · 숫자 · - 를 점으로 이은 것)이면 그대로")
    void eventTypes(String value) {
        assertThat(McpSafeText.eventType(value)).isEqualTo(value);
    }

    @ParameterizedTest(name = "[{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"Order.Created", "order created", "order", "order.created; drop", "홍길동.created"})
    @DisplayName("eventType 형식이 아니면 null")
    void notEventTypes(String value) {
        assertThat(McpSafeText.eventType(value)).isNull();
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"IllegalStateException", "DeserializationException", "ListenerExecutionFailedException"})
    @DisplayName("예외 이름(클래스 단순 이름)이면 그대로")
    void exceptionNames(String value) {
        assertThat(McpSafeText.exceptionName(value)).isEqualTo(value);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "org.springframework.kafka.support.serializer.DeserializationException, DeserializationException",
        "java.lang.IllegalStateException, IllegalStateException",
        "org.springframework.kafka.listener.ListenerExecutionFailedException, ListenerExecutionFailedException",
        "com.bbcc.kidly.Outer$InnerException, InnerException"})
    @DisplayName("저장 형식인 전체 이름(패키지 붙음)은 단순 이름으로 잘라 내보낸다")
    void fullyQualifiedNames(String stored, String simple) {
        assertThat(McpSafeText.exceptionName(stored)).isEqualTo(simple);
    }

    @ParameterizedTest(name = "[{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"java.lang.IllegalStateException: 회원 13 없음", "IllegalState Exception",
        "illegalStateException", "Exception\n\tat x", "java.lang.IllegalState Exception", "Java.Lang.Bad",
        ".IllegalStateException", "java..IllegalStateException", "java.lang.", "java.lang.illegalState",
        "java.lang.IllegalStateException:", "com.x.Outer$"})
    @DisplayName("예외 메시지 · 스택 · 패키지가 붙으면 null")
    void notExceptionNames(String value) {
        assertThat(McpSafeText.exceptionName(value)).isNull();
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"12", "9300001", "0b6f3c1e-8a2d-4c5b-9e7f-1a2b3c4d5e6f"})
    @DisplayName("메시지 키 · 이벤트 ID는 숫자 ID나 UUID일 때만")
    void identifiers(String value) {
        assertThat(McpSafeText.identifier(value)).isEqualTo(value);
    }

    @ParameterizedTest(name = "[{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"user@example.com", "홍길동", "12 34", "-1", "0b6f3c1e8a2d"})
    @DisplayName("숫자 ID · UUID가 아니면 null")
    void notIdentifiers(String value) {
        assertThat(McpSafeText.identifier(value)).isNull();
    }
}
