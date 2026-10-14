package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.MEMBER_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class ChargeApiTest {

    private static final String CHARGES_PATH = "/api/v1/wallets/me/charges";
    private static final String CONFIRM_PATH = CHARGES_PATH + "/confirm";
    private static final Long OTHER_MEMBER_ID = 2L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Autowired
    private WalletRepository walletRepository;

    @BeforeEach
    void setUp() {
        walletRepository.save(Wallet.create(MEMBER_ID));
    }

    @Test
    @DisplayName("충전을 요청하면 201과 Location, 토스 주문번호를 주고 조회하면 대기 중이다")
    void request() {
        RequestedCharge charge = requestCharge(10_000);

        assertThat(charge.orderId()).hasSize(36);
        client.get().uri(charge.location())
            .headers(headers -> headers.setBearerAuth(tokens.buyer(MEMBER_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.orderId").isEqualTo(charge.orderId())
            .jsonPath("$.amount").isEqualTo(10_000)
            .jsonPath("$.status").isEqualTo("PENDING")
            .jsonPath("$.approvedAt").isEmpty()
            .jsonPath("$.createdAt").isNotEmpty();
    }

    @Test
    @DisplayName("1천 원보다 적으면 400 CHARGE_AMOUNT_OUT_OF_RANGE")
    void amountOutOfRange() {
        client.post().uri(CHARGES_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(MEMBER_ID)))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("amount", 999))
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.title").isEqualTo("충전할 수 없는 금액입니다");
    }

    @Test
    @DisplayName("토스가 승인하면 200 DONE과 늘어난 잔액을 주고 지갑 잔액도 늘어 있다")
    void confirmApproved() {
        RequestedCharge charge = requestCharge(10_000);

        confirm(MEMBER_ID, "payment-key", charge.orderId(), 10_000)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("DONE")
            .jsonPath("$.amount").isEqualTo(10_000)
            .jsonPath("$.balance").isEqualTo(10_000);
        client.get().uri("/api/v1/wallets/me")
            .headers(headers -> headers.setBearerAuth(tokens.buyer(MEMBER_ID)))
            .exchange()
            .expectBody()
            .jsonPath("$.balance").isEqualTo(10_000);
    }

    @Test
    @DisplayName("토스가 거절하면 400 CHARGE_REJECTED와 토스 메시지를 주고 충전은 실패 상태다")
    void confirmRejected() {
        RequestedCharge charge = requestCharge(10_000);

        confirm(MEMBER_ID, FakePaymentGateway.REJECTED_KEY, charge.orderId(), 10_000)
            .expectStatus().isBadRequest()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.title").isEqualTo("충전 승인이 거절됐습니다")
            .jsonPath("$.detail").isEqualTo(FakePaymentGateway.REJECTED_MESSAGE);
        expectStatus(charge, "FAILED");
    }

    @Test
    @DisplayName("토스 결과를 모르면 202 CONFIRMING이고 잔액은 null이다")
    void confirmUnknown() {
        RequestedCharge charge = requestCharge(10_000);

        confirm(MEMBER_ID, FakePaymentGateway.UNKNOWN_KEY, charge.orderId(), 10_000)
            .expectStatus().isAccepted()
            .expectBody()
            .jsonPath("$.status").isEqualTo("CONFIRMING")
            .jsonPath("$.balance").isEmpty();
        expectStatus(charge, "CONFIRMING");
    }

    @Test
    @DisplayName("결제한 금액이 요청한 충전 금액과 다르면 400 CHARGE_AMOUNT_MISMATCH")
    void confirmAmountMismatch() {
        RequestedCharge charge = requestCharge(10_000);

        confirm(MEMBER_ID, "payment-key", charge.orderId(), 9_000)
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.title").isEqualTo("충전 금액이 다릅니다");
        expectStatus(charge, "PENDING");
    }

    @Test
    @DisplayName("남의 충전을 승인하면 404 CHARGE_NOT_FOUND")
    void confirmOthersCharge() {
        walletRepository.save(Wallet.create(OTHER_MEMBER_ID));
        RequestedCharge charge = requestCharge(10_000);

        confirm(OTHER_MEMBER_ID, "payment-key", charge.orderId(), 10_000)
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.title").isEqualTo("충전을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("충전 목록은 내 충전만 최신순 페이지로 주고, 상태와 상관없이 모두 포함한다")
    void getCharges() {
        RequestedCharge done = requestCharge(10_000);
        confirm(MEMBER_ID, "payment-key", done.orderId(), 10_000).expectStatus().isOk();
        RequestedCharge pending = requestCharge(20_000);
        walletRepository.save(Wallet.create(OTHER_MEMBER_ID));
        requestCharge(OTHER_MEMBER_ID, 30_000);

        getCharges(MEMBER_ID, "?size=1")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(1)
            .jsonPath("$.content[0].orderId").isEqualTo(pending.orderId())
            .jsonPath("$.content[0].amount").isEqualTo(20_000)
            .jsonPath("$.content[0].status").isEqualTo("PENDING")
            .jsonPath("$.content[0].approvedAt").isEmpty()
            .jsonPath("$.content[0].createdAt").isNotEmpty()
            .jsonPath("$.page").isEqualTo(0)
            .jsonPath("$.size").isEqualTo(1)
            .jsonPath("$.totalElements").isEqualTo(2)
            .jsonPath("$.totalPages").isEqualTo(2)
            .jsonPath("$.hasNext").isEqualTo(true);
        getCharges(MEMBER_ID, "?page=1&size=1")
            .expectBody()
            .jsonPath("$.content[0].orderId").isEqualTo(done.orderId())
            .jsonPath("$.content[0].status").isEqualTo("DONE")
            .jsonPath("$.content[0].approvedAt").isNotEmpty()
            .jsonPath("$.hasNext").isEqualTo(false);
    }

    @Test
    @DisplayName("충전이 없으면 빈 목록이다")
    void emptyCharges() {
        getCharges(MEMBER_ID, "")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content").isArray()
            .jsonPath("$.content.length()").isEqualTo(0)
            .jsonPath("$.totalElements").isEqualTo(0);
    }

    @Test
    @DisplayName("충전 목록의 size가 100을 넘으면 400")
    void tooLargePageSize() {
        getCharges(MEMBER_ID, "?size=101")
            .expectStatus().isBadRequest()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    @DisplayName("로그인하지 않으면 충전 목록도 401")
    void getChargesWithoutToken() {
        client.get().uri(CHARGES_PATH)
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("로그인하지 않으면 401")
    void withoutToken() {
        client.post().uri(CHARGES_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("amount", 10_000))
            .exchange()
            .expectStatus().isUnauthorized();
    }

    private RequestedCharge requestCharge(int amount) {
        return requestCharge(MEMBER_ID, amount);
    }

    private RequestedCharge requestCharge(Long memberId, int amount) {
        var result = client.post().uri(CHARGES_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(memberId)))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("amount", amount))
            .exchange()
            .expectStatus().isCreated()
            .expectBody(RequestedChargeBody.class)
            .returnResult();
        URI location = result.getResponseHeaders().getLocation();
        RequestedChargeBody body = result.getResponseBody();
        assertThat(location).isNotNull();
        assertThat(body).isNotNull();
        assertThat(location.getPath()).isEqualTo(CHARGES_PATH + "/" + body.chargeId());
        assertThat(body.amount()).isEqualTo(amount);
        assertThat(body.expiresAt()).isNotNull();
        return new RequestedCharge(location, body.orderId());
    }

    private RestTestClient.ResponseSpec confirm(Long memberId, String paymentKey, String orderId, int amount) {
        return client.post().uri(CONFIRM_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(memberId)))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("paymentKey", paymentKey, "orderId", orderId, "amount", amount))
            .exchange();
    }

    private RestTestClient.ResponseSpec getCharges(Long memberId, String query) {
        return client.get().uri(CHARGES_PATH + query)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(memberId)))
            .exchange();
    }

    private void expectStatus(RequestedCharge charge, String status) {
        client.get().uri(charge.location())
            .headers(headers -> headers.setBearerAuth(tokens.buyer(MEMBER_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo(status);
    }

    private record RequestedChargeBody(Long chargeId, String orderId, long amount, String expiresAt) {
    }

    private record RequestedCharge(URI location, String orderId) {
    }
}
