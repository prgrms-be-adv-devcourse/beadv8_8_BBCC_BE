package com.bbcc.kidly.boundedcontext.order.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 밖으로 내보내는 글 칸 거르기(#925): 정해진 형식만 그대로, 자유 문구 · 메시지가 섞인 값 · 빈 값 · 너무 긴 값은 null.
 */
class OrderTextGuardTest {

    @Test
    @DisplayName("exceptionClassOnly: '예외클래스: 메시지'에서 클래스 이름만, 단순 · 전체 이름 · 중첩($) 모두")
    void exceptionClassOnly() {
        assertThat(OrderTextGuard.exceptionClassOnly("RecordTooLargeException: 홍길동 010-1234-5678 서울시"))
            .isEqualTo("RecordTooLargeException");
        assertThat(OrderTextGuard.exceptionClassOnly("org.apache.kafka.common.errors.TimeoutException: x"))
            .isEqualTo("org.apache.kafka.common.errors.TimeoutException");
        assertThat(OrderTextGuard.exceptionClassOnly("Outer$Inner")).isEqualTo("Outer$Inner");
        assertThat(OrderTextGuard.exceptionClassOnly(" IllegalStateException : null"))
            .isEqualTo("IllegalStateException");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"홍길동님 주문 실패", "010-1234-5678: 실패", "bad class: x", "a..b", ".Leading", " ", ":메시지만"})
    @DisplayName("exceptionClassOnly: 클래스 이름 형식이 아니면 null")
    void exceptionClassOnlyRejects(String value) {
        assertThat(OrderTextGuard.exceptionClassOnly(value)).isNull();
    }

    @Test
    @DisplayName("exceptionClassOnly: 200자를 넘는 이름은 null")
    void exceptionClassTooLong() {
        assertThat(OrderTextGuard.exceptionClassOnly("A".repeat(201) + ": x")).isNull();
    }

    @Test
    @DisplayName("codeOrNull: 대문자 코드만(1~64자)")
    void codeOrNull() {
        assertThat(OrderTextGuard.codeOrNull("OUT_OF_STOCK")).isEqualTo("OUT_OF_STOCK");
        assertThat(OrderTextGuard.codeOrNull("A".repeat(64))).hasSize(64);
        assertThat(OrderTextGuard.codeOrNull("A".repeat(65))).isNull();
        assertThat(OrderTextGuard.codeOrNull("out_of_stock")).isNull();
        assertThat(OrderTextGuard.codeOrNull("품절")).isNull();
        assertThat(OrderTextGuard.codeOrNull("OUT OF STOCK")).isNull();
        assertThat(OrderTextGuard.codeOrNull("")).isNull();
        assertThat(OrderTextGuard.codeOrNull(null)).isNull();
    }

    @Test
    @DisplayName("failureCodeOrNull: 대문자 코드 · 소문자 줄표 코드 · 예외 단순 이름만. 연락처 · 문장 · 한글 · 긴 값은 null")
    void failureCodeOrNull() {
        assertThat(OrderTextGuard.failureCodeOrNull("SELLER_CANCELLED")).isEqualTo("SELLER_CANCELLED");
        assertThat(OrderTextGuard.failureCodeOrNull("member-mismatch")).isEqualTo("member-mismatch");
        assertThat(OrderTextGuard.failureCodeOrNull("DataIntegrityViolationException"))
            .isEqualTo("DataIntegrityViolationException");
        assertThat(OrderTextGuard.failureCodeOrNull("010-1234-5678")).isNull();
        assertThat(OrderTextGuard.failureCodeOrNull("홍길동")).isNull();
        assertThat(OrderTextGuard.failureCodeOrNull("customer asked to cancel")).isNull();
        assertThat(OrderTextGuard.failureCodeOrNull("IllegalStateException: 홍길동")).isNull();
        assertThat(OrderTextGuard.failureCodeOrNull("a@b.com")).isNull();
        assertThat(OrderTextGuard.failureCodeOrNull("x".repeat(65))).isNull();
        assertThat(OrderTextGuard.failureCodeOrNull(null)).isNull();
    }

    @Test
    @DisplayName("sourceOrNull: HTTP 경로 틀 · scheduler:작업 · 이벤트 타입만")
    void sourceOrNull() {
        assertThat(OrderTextGuard.sourceOrNull("POST /api/v1/orders")).isEqualTo("POST /api/v1/orders");
        assertThat(OrderTextGuard.sourceOrNull("POST /api/v1/seller/orders/{sellerOrderId}/cancel"))
            .isEqualTo("POST /api/v1/seller/orders/{sellerOrderId}/cancel");
        assertThat(OrderTextGuard.sourceOrNull("scheduler:payment-expiry")).isEqualTo("scheduler:payment-expiry");
        assertThat(OrderTextGuard.sourceOrNull("payment.completed")).isEqualTo("payment.completed");
        assertThat(OrderTextGuard.sourceOrNull("stock.deduction-failed")).isEqualTo("stock.deduction-failed");
        assertThat(OrderTextGuard.sourceOrNull("POST /api/v1/orders?name=홍길동")).isNull();
        assertThat(OrderTextGuard.sourceOrNull("POST /api/v1/orders/홍길동")).isNull();
        assertThat(OrderTextGuard.sourceOrNull("관리자 수동 처리 010-1234-5678")).isNull();
        assertThat(OrderTextGuard.sourceOrNull("scheduler:홍길동")).isNull();
        assertThat(OrderTextGuard.sourceOrNull("payment")).isNull();
        assertThat(OrderTextGuard.sourceOrNull("POST /api/v1/" + "a/".repeat(100) + "b")).isNull();
        assertThat(OrderTextGuard.sourceOrNull("")).isNull();
        assertThat(OrderTextGuard.sourceOrNull(null)).isNull();
    }

    @Test
    @DisplayName("idOrNull · producerOrNull · eventTypeOrNull: 숫자 · UUID / 소문자 도메인 이름 / 이벤트 타입만")
    void identifiers() {
        assertThat(OrderTextGuard.idOrNull("42")).isEqualTo("42");
        assertThat(OrderTextGuard.idOrNull("3f2504e0-4f89-11d3-9a0c-0305e82c3301"))
            .isEqualTo("3f2504e0-4f89-11d3-9a0c-0305e82c3301");
        assertThat(OrderTextGuard.idOrNull("010-1234-5678")).isNull();
        assertThat(OrderTextGuard.idOrNull("홍길동")).isNull();
        assertThat(OrderTextGuard.idOrNull("1".repeat(20))).isNull();
        assertThat(OrderTextGuard.producerOrNull("payment")).isEqualTo("payment");
        assertThat(OrderTextGuard.producerOrNull("Payment Team")).isNull();
        assertThat(OrderTextGuard.eventTypeOrNull("order-item.cancelled")).isEqualTo("order-item.cancelled");
        assertThat(OrderTextGuard.eventTypeOrNull("주문.취소")).isNull();
    }
}
