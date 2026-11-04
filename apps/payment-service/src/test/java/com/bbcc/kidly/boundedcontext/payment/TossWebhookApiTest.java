package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.MEMBER_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.payment.app.port.PaymentKeyCipher;
import com.bbcc.kidly.boundedcontext.payment.domain.Charge;
import com.bbcc.kidly.boundedcontext.payment.domain.ChargeRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.ChargeStatus;
import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.support.FunctionalTest;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 토스 웹훅은 토큰 없이 들어온다. 토스 조회 결과는 FakePaymentGateway로 정한다.
 */
@FunctionalTest
class TossWebhookApiTest {

    private static final String WEBHOOK_PATH = "/api/v1/webhooks/toss";

    @Autowired
    private RestTestClient client;

    @Autowired
    private FakePaymentGateway fakePaymentGateway;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private ChargeRepository chargeRepository;

    @Autowired
    private PaymentKeyCipher paymentKeyCipher;

    private Wallet wallet;

    @BeforeEach
    void setUp() {
        Wallet created = Wallet.create(MEMBER_ID);
        created.deposit(15_000L);
        wallet = walletRepository.save(created);
    }

    @AfterEach
    void resetGateway() {
        fakePaymentGateway.reset();
    }

    @Test
    @DisplayName("토스에서 직접 취소됐다는 웹훅을 받으면 토큰 없이도 200이고, 충전은 취소 완료 · 잔액은 충전액만큼 준다")
    void cancelled() {
        Charge charge = doneCharge(10_000L);
        fakePaymentGateway.willFindCancelled(charge.getTossOrderId());

        webhook("PAYMENT_STATUS_CHANGED", charge.getTossOrderId(), "CANCELED")
            .expectStatus().isOk();

        assertThat(statusOf(charge)).isEqualTo(ChargeStatus.CANCELLED);
        assertThat(balance()).isEqualTo(5_000L);
    }

    @Test
    @DisplayName("토스 조회 결과를 모르면 503 problem+json을 줘서 토스가 다시 보내게 하고, 충전은 그대로다")
    void unknown() {
        Charge charge = doneCharge(10_000L);

        webhook("PAYMENT_STATUS_CHANGED", charge.getTossOrderId(), "CANCELED")
            .expectStatus().isEqualTo(503)
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.title").isEqualTo("결제대행사 결과를 확인하지 못했습니다");

        assertThat(statusOf(charge)).isEqualTo(ChargeStatus.DONE);
        assertThat(balance()).isEqualTo(15_000L);
    }

    @Test
    @DisplayName("취소가 아닌 상태 변경이나 다른 이벤트는 토스를 조회하지 않고 200으로 넘어간다")
    void ignored() {
        Charge charge = doneCharge(10_000L);
        fakePaymentGateway.willFindCancelled(charge.getTossOrderId());

        webhook("PAYMENT_STATUS_CHANGED", charge.getTossOrderId(), "DONE").expectStatus().isOk();
        webhook("DEPOSIT_CALLBACK", charge.getTossOrderId(), "CANCELED").expectStatus().isOk();

        assertThat(statusOf(charge)).isEqualTo(ChargeStatus.DONE);
        assertThat(balance()).isEqualTo(15_000L);
    }

    @Test
    @DisplayName("우리 충전이 아닌 주문번호면 200으로 넘어간다")
    void unknownOrder() {
        webhook("PAYMENT_STATUS_CHANGED", "not-our-order", "CANCELED")
            .expectStatus().isOk();
    }

    private RestTestClient.ResponseSpec webhook(String eventType, String orderId, String status) {
        return client.post().uri(WEBHOOK_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .body("""
                {"eventType": "%s", "createdAt": "2026-10-15T10:00:00.000000",
                 "data": {"paymentKey": "pk-1", "orderId": "%s", "status": "%s", "totalAmount": 10000}}
                """.formatted(eventType, orderId, status))
            .exchange();
    }

    // 지갑 입금 없이 충전만 완료로 저장한다. 잔액은 setUp에서 넣어 둔 15,000원이다
    private Charge doneCharge(long amount) {
        Charge charge = Charge.request(wallet.getId(), UUID.randomUUID().toString(), amount,
            LocalDateTime.now().plusMinutes(30));
        charge.startConfirming(amount, LocalDateTime.now());
        charge.approve(paymentKeyCipher.encrypt("payment-key"), LocalDateTime.now());
        return chargeRepository.save(charge);
    }

    private ChargeStatus statusOf(Charge charge) {
        return chargeRepository.findById(charge.getId()).orElseThrow().getStatus();
    }

    private long balance() {
        return walletRepository.findByMemberId(MEMBER_ID).orElseThrow().getBalance();
    }
}
