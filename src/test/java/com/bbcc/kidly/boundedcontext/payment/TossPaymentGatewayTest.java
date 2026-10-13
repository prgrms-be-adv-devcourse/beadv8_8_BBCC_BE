package com.bbcc.kidly.boundedcontext.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.bbcc.kidly.boundedcontext.payment.app.port.PgApproval;
import com.bbcc.kidly.boundedcontext.payment.app.port.PgCancellation;
import com.bbcc.kidly.boundedcontext.payment.app.port.PgResult;
import com.bbcc.kidly.boundedcontext.payment.out.toss.TossPaymentGateway;
import com.bbcc.kidly.boundedcontext.payment.out.toss.TossProperties;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TossPaymentGatewayTest {

    private static final String BASE_URL = "https://api.tosspayments.com";
    // "test_sk_key:"를 Base64로 인코딩한 값
    private static final String AUTHORIZATION = "Basic dGVzdF9za19rZXk6";
    private static final String APPROVED_AT = "2026-10-13T10:00:00+09:00";
    private static final String CANCELED_AT = "2026-10-13T11:00:00+09:00";

    private MockRestServiceServer server;
    private TossPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        TossProperties properties = new TossProperties(
            "test_sk_key", BASE_URL, Duration.ofSeconds(3), Duration.ofSeconds(30));
        gateway = new TossPaymentGateway(builder, properties);
    }

    @Test
    @DisplayName("승인에 성공하면 paymentKey와 승인 금액, 승인 시각을 돌려준다")
    void confirmSucceeds() {
        server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", AUTHORIZATION))
            .andExpect(header("Idempotency-Key", "idem-1"))
            .andExpect(content().json("""
                {"paymentKey": "pk-1", "orderId": "charge-1", "amount": 10000}
                """))
            .andRespond(withSuccess(payment("DONE", APPROVED_AT), MediaType.APPLICATION_JSON));

        PgResult<PgApproval> result = gateway.confirm("pk-1", "charge-1", 10_000L, "idem-1");

        assertThat(result).isEqualTo(new PgResult.Success<>(new PgApproval("pk-1", 10_000L, local(APPROVED_AT))));
        server.verify();
    }

    @Test
    @DisplayName("토스가 승인을 거절하면 토스 오류 코드를 실패 코드로 돌려준다")
    void confirmRejected() {
        server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
            .andRespond(withStatus(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(error("REJECT_CARD_PAYMENT", "한도초과 혹은 잔액부족으로 결제에 실패했습니다.")));

        PgResult<PgApproval> result = gateway.confirm("pk-1", "charge-1", 10_000L, "idem-1");

        assertThat(result).isEqualTo(
            new PgResult.Rejected<>("REJECT_CARD_PAYMENT", "한도초과 혹은 잔액부족으로 결제에 실패했습니다."));
    }

    @Test
    @DisplayName("응답 전에 시간이 초과되면 결과를 모르는 것으로 돌려준다")
    void confirmTimesOut() {
        server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
            .andRespond(withException(new SocketTimeoutException("Read timed out")));

        PgResult<PgApproval> result = gateway.confirm("pk-1", "charge-1", 10_000L, "idem-1");

        assertThat(result).isInstanceOf(PgResult.Unknown.class);
    }

    @Test
    @DisplayName("토스 서버 오류(5xx)는 결과를 모르는 것으로 돌려준다")
    void confirmServerError() {
        server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
            .andRespond(withServerError()
                .contentType(MediaType.APPLICATION_JSON)
                .body(error("FAILED_INTERNAL_SYSTEM_PROCESSING", "내부 시스템 처리 작업이 실패했습니다.")));

        PgResult<PgApproval> result = gateway.confirm("pk-1", "charge-1", 10_000L, "idem-1");

        assertThat(result).isInstanceOf(PgResult.Unknown.class);
    }

    @Test
    @DisplayName("이미 처리된 결제라는 응답은 승인됐을 수 있으므로 결과를 모르는 것으로 돌려준다")
    void confirmAlreadyProcessed() {
        server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(error("ALREADY_PROCESSED_PAYMENT", "이미 처리된 결제 입니다.")));

        PgResult<PgApproval> result = gateway.confirm("pk-1", "charge-1", 10_000L, "idem-1");

        assertThat(result).isInstanceOf(PgResult.Unknown.class);
    }

    @Test
    @DisplayName("취소에 성공하면 마지막 취소 시각을 돌려준다")
    void cancelSucceeds() {
        server.expect(requestTo(BASE_URL + "/v1/payments/pk-1/cancel"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", AUTHORIZATION))
            .andExpect(header("Idempotency-Key", "idem-2"))
            .andExpect(content().json("""
                {"cancelReason": "단순 변심"}
                """))
            .andRespond(withSuccess(canceledPayment(), MediaType.APPLICATION_JSON));

        PgResult<PgCancellation> result = gateway.cancel("pk-1", "단순 변심", "idem-2");

        assertThat(result).isEqualTo(new PgResult.Success<>(new PgCancellation(local(CANCELED_AT))));
    }

    @Test
    @DisplayName("주문번호로 조회한 결제가 완료 상태면 승인 결과를 돌려준다")
    void findDonePayment() {
        server.expect(requestTo(BASE_URL + "/v1/payments/orders/charge-1"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", AUTHORIZATION))
            .andRespond(withSuccess(payment("DONE", APPROVED_AT), MediaType.APPLICATION_JSON));

        PgResult<PgApproval> result = gateway.findByOrderId("charge-1");

        assertThat(result).isEqualTo(new PgResult.Success<>(new PgApproval("pk-1", 10_000L, local(APPROVED_AT))));
    }

    @Test
    @DisplayName("조회한 결제가 승인되지 않고 끝났으면 토스 결제 상태를 실패 코드로 돌려준다")
    void findAbortedPayment() {
        server.expect(requestTo(BASE_URL + "/v1/payments/orders/charge-1"))
            .andRespond(withSuccess(payment("ABORTED", null), MediaType.APPLICATION_JSON));

        PgResult<PgApproval> result = gateway.findByOrderId("charge-1");

        assertThat(result).isInstanceOf(PgResult.Rejected.class)
            .extracting("code").isEqualTo("ABORTED");
    }

    @Test
    @DisplayName("조회한 결제가 아직 진행 중이면 결과를 모르는 것으로 돌려준다")
    void findInProgressPayment() {
        server.expect(requestTo(BASE_URL + "/v1/payments/orders/charge-1"))
            .andRespond(withSuccess(payment("IN_PROGRESS", null), MediaType.APPLICATION_JSON));

        PgResult<PgApproval> result = gateway.findByOrderId("charge-1");

        assertThat(result).isInstanceOf(PgResult.Unknown.class);
    }

    @Test
    @DisplayName("토스에 결제가 없으면 승인되지 않은 것이므로 거절로 돌려준다")
    void findMissingPayment() {
        server.expect(requestTo(BASE_URL + "/v1/payments/orders/charge-1"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(error("NOT_FOUND_PAYMENT", "존재하지 않는 결제 정보 입니다.")));

        PgResult<PgApproval> result = gateway.findByOrderId("charge-1");

        assertThat(result).isInstanceOf(PgResult.Rejected.class)
            .extracting("code").isEqualTo("NOT_FOUND_PAYMENT");
    }

    private static String payment(String status, String approvedAt) {
        String approved = approvedAt == null ? "null" : "\"" + approvedAt + "\"";
        return """
            {"paymentKey": "pk-1", "orderId": "charge-1", "status": "%s", "totalAmount": 10000,
             "approvedAt": %s, "method": "카드", "cancels": null}
            """.formatted(status, approved);
    }

    private static String canceledPayment() {
        return """
            {"paymentKey": "pk-1", "orderId": "charge-1", "status": "CANCELED", "totalAmount": 10000,
             "approvedAt": "%s",
             "cancels": [{"cancelAmount": 10000, "cancelReason": "단순 변심", "canceledAt": "%s"}]}
            """.formatted(APPROVED_AT, CANCELED_AT);
    }

    private static String error(String code, String message) {
        return """
            {"code": "%s", "message": "%s"}
            """.formatted(code, message);
    }

    // 토스 시각(+09:00)을 서버 기본 시간대의 LocalDateTime으로 바꾼 값
    private static LocalDateTime local(String offsetDateTime) {
        return OffsetDateTime.parse(offsetDateTime).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }
}
