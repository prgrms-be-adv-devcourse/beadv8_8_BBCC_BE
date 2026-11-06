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
        assertThat(OrderTextGuard.exceptionClassOnly("org.apache.kafka.common.errors.RecordTooLargeException"))
            .isEqualTo("org.apache.kafka.common.errors.RecordTooLargeException");
        assertThat(OrderTextGuard.exceptionClassOnly("Outer$InnerError")).isEqualTo("Outer$InnerError");
        assertThat(OrderTextGuard.exceptionClassOnly(" IllegalStateException : null"))
            .isEqualTo("IllegalStateException");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"홍길동님 주문 실패", "010-1234-5678: 실패", "bad class: x", "a..b", ".Leading", " ", ":메시지만",
        "Outer$Inner", "JohnSmith", "john.smith", "hong.gildong", "Kim: 연락처", "com.example.Exceptional"})
    @DisplayName("exceptionClassOnly: 마지막 마디가 Exception · Error로 끝나는 클래스 이름이 아니면 null(이름 · 점 이름 등 ASCII 개인정보 형태 포함)")
    void exceptionClassOnlyRejects(String value) {
        assertThat(OrderTextGuard.exceptionClassOnly(value)).isNull();
    }

    @Test
    @DisplayName("exceptionClassOnly: 200자를 넘는 이름은 null")
    void exceptionClassTooLong() {
        assertThat(OrderTextGuard.exceptionClassOnly("A".repeat(192) + "Exception: x")).isNull();
    }

    @Test
    @DisplayName("codeOrNull: 대문자로 시작하는 대문자 코드만(1~64자). 숫자만(전화번호 · 주민번호 형태)은 null")
    void codeOrNull() {
        assertThat(OrderTextGuard.codeOrNull("OUT_OF_STOCK")).isEqualTo("OUT_OF_STOCK");
        assertThat(OrderTextGuard.codeOrNull("A".repeat(64))).hasSize(64);
        assertThat(OrderTextGuard.codeOrNull("A".repeat(65))).isNull();
        assertThat(OrderTextGuard.codeOrNull("out_of_stock")).isNull();
        assertThat(OrderTextGuard.codeOrNull("품절")).isNull();
        assertThat(OrderTextGuard.codeOrNull("OUT OF STOCK")).isNull();
        assertThat(OrderTextGuard.codeOrNull("01098765432")).isNull();
        assertThat(OrderTextGuard.codeOrNull("9001011234567")).isNull();
        assertThat(OrderTextGuard.codeOrNull("_X")).isNull();
        assertThat(OrderTextGuard.codeOrNull("")).isNull();
        assertThat(OrderTextGuard.codeOrNull(null)).isNull();
    }

    @Test
    @DisplayName("failureCodeOrNull: 대문자 코드 · 결제 불일치 사유 코드 목록 · …Exception/…Error 단순 이름만. "
        + "연락처 · 이름 · 문장 · 한글 · 긴 값은 null")
    void failureCodeOrNull() {
        assertThat(OrderTextGuard.failureCodeOrNull("SELLER_CANCELLED")).isEqualTo("SELLER_CANCELLED");
        assertThat(OrderTextGuard.failureCodeOrNull("member-mismatch")).isEqualTo("member-mismatch");
        assertThat(OrderTextGuard.failureCodeOrNull("DataIntegrityViolationException"))
            .isEqualTo("DataIntegrityViolationException");
        assertThat(OrderTextGuard.failureCodeOrNull("total-amount-mismatch")).isEqualTo("total-amount-mismatch");
        assertThat(OrderTextGuard.failureCodeOrNull("OutOfMemoryError")).isEqualTo("OutOfMemoryError");
        assertThat(OrderTextGuard.failureCodeOrNull("010-1234-5678")).isNull();
        assertThat(OrderTextGuard.failureCodeOrNull("01098765432")).isNull();
        assertThat(OrderTextGuard.failureCodeOrNull("hong-gildong")).isNull();
        assertThat(OrderTextGuard.failureCodeOrNull("kim")).isNull();
        assertThat(OrderTextGuard.failureCodeOrNull("JohnSmith")).isNull();
        assertThat(OrderTextGuard.failureCodeOrNull("Hong")).isNull();
        assertThat(OrderTextGuard.failureCodeOrNull("john.smith")).isNull();
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
    @DisplayName("idOrNull: 0으로 시작하지 않는 숫자 · UUID만. 전화번호(0으로 시작) · 이름 · 20자리는 null")
    void idOrNull() {
        assertThat(OrderTextGuard.idOrNull("42")).isEqualTo("42");
        assertThat(OrderTextGuard.idOrNull("3f2504e0-4f89-11d3-9a0c-0305e82c3301"))
            .isEqualTo("3f2504e0-4f89-11d3-9a0c-0305e82c3301");
        for (String personal : new String[] {"01098765432", "0101234567890", "010-1234-5678", "0", "hong-gildong",
            "kim", "JohnSmith", "john.smith", "홍길동", "1".repeat(20)}) {
            assertThat(OrderTextGuard.idOrNull(personal)).as(personal).isNull();
        }
    }

    @Test
    @DisplayName("orderRefOrNull: 관련 주문 ID와 같거나 UUID일 때만. 다른 숫자(주민번호 · 전화번호 형태)는 null")
    void orderRefOrNull() {
        assertThat(OrderTextGuard.orderRefOrNull("42", 42L)).isEqualTo("42");
        assertThat(OrderTextGuard.orderRefOrNull("3f2504e0-4f89-11d3-9a0c-0305e82c3301", 42L))
            .isEqualTo("3f2504e0-4f89-11d3-9a0c-0305e82c3301");
        assertThat(OrderTextGuard.orderRefOrNull("9001011234567", 42L)).isNull();
        assertThat(OrderTextGuard.orderRefOrNull("01098765432", 42L)).isNull();
        assertThat(OrderTextGuard.orderRefOrNull("43", 42L)).isNull();
        assertThat(OrderTextGuard.orderRefOrNull("42", null)).isNull();
        assertThat(OrderTextGuard.orderRefOrNull(null, 42L)).isNull();
    }

    @Test
    @DisplayName("받은 producer · eventType: 원래 토픽 kidly.{x}.events의 x와 같을 때만(eventType은 앞 마디). 토픽이 형식 밖이면 둘 다 null")
    void receivedProducerAndEventType() {
        String payment = "kidly.payment.events";
        assertThat(OrderTextGuard.receivedProducerOrNull("payment", payment)).isEqualTo("payment");
        assertThat(OrderTextGuard.receivedEventTypeOrNull("payment.completed", payment)).isEqualTo("payment.completed");
        assertThat(OrderTextGuard.receivedProducerOrNull("product", payment)).isNull();
        assertThat(OrderTextGuard.receivedProducerOrNull("kim", payment)).isNull();
        assertThat(OrderTextGuard.receivedEventTypeOrNull("hong.gildong", payment)).isNull();
        assertThat(OrderTextGuard.receivedEventTypeOrNull("john.smith", payment)).isNull();
        assertThat(OrderTextGuard.receivedEventTypeOrNull("payment", payment)).isNull();
        assertThat(OrderTextGuard.receivedEventTypeOrNull("payment.홍길동", payment)).isNull();
        assertThat(OrderTextGuard.receivedProducerOrNull("payment", "payment-dlt")).isNull();
        assertThat(OrderTextGuard.receivedEventTypeOrNull("payment.completed", "kidly.payment.events.order.dlt"))
            .isNull();
        assertThat(OrderTextGuard.receivedEventTypeOrNull("payment.completed", null)).isNull();
    }

    @Test
    @DisplayName("받은 eventType 토픽별 허용 앞 마디: product 토픽은 product. · stock., childprofile 토픽은 child.만. "
        + "같은 토픽의 다른 앞 마디는 null")
    void receivedEventTypePerTopic() {
        String product = "kidly.product.events";
        assertThat(OrderTextGuard.receivedEventTypeOrNull("stock.deducted", product)).isEqualTo("stock.deducted");
        assertThat(OrderTextGuard.receivedEventTypeOrNull("stock.deduction-failed", product))
            .isEqualTo("stock.deduction-failed");
        assertThat(OrderTextGuard.receivedEventTypeOrNull("product.changed", product)).isEqualTo("product.changed");
        assertThat(OrderTextGuard.receivedEventTypeOrNull("hong.gildong", product)).isNull();
        assertThat(OrderTextGuard.receivedEventTypeOrNull("payment.completed", product)).isNull();
        assertThat(OrderTextGuard.receivedEventTypeOrNull("child.changed", "kidly.childprofile.events"))
            .isEqualTo("child.changed");
        assertThat(OrderTextGuard.receivedEventTypeOrNull("childprofile.changed", "kidly.childprofile.events"))
            .isNull();
        assertThat(OrderTextGuard.receivedEventTypeOrNull("seller.application-rejected", "kidly.seller.events"))
            .isEqualTo("seller.application-rejected");
        assertThat(OrderTextGuard.receivedEventTypeOrNull("stock.deducted", "kidly.payment.events")).isNull();
        String auth = "kidly.auth.events";
        assertThat(OrderTextGuard.receivedEventTypeOrNull("child-account.changed", auth))
            .isEqualTo("child-account.changed");
        assertThat(OrderTextGuard.receivedEventTypeOrNull("auth.tokens-revoked", auth)).isNull();
        assertThat(OrderTextGuard.receivedEventTypeOrNull("hong.gildong", auth)).isNull();
    }

    @Test
    @DisplayName("sourceOrNull의 이벤트 타입은 주문 코드가 만든 값 그대로(payment.completed). 단어 하나(payment)는 null")
    void sourceEventType() {
        assertThat(OrderTextGuard.sourceOrNull("payment.completed")).isEqualTo("payment.completed");
        assertThat(OrderTextGuard.sourceOrNull("payment")).isNull();
    }
}
