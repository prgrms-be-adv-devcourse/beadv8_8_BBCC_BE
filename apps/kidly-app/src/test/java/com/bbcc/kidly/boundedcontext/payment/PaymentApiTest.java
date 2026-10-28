package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.NOW;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.WALLET_ID;

import com.bbcc.kidly.boundedcontext.payment.domain.Payment;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentItem;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentRepository;
import com.bbcc.kidly.shared.payment.domain.PaymentFailureCode;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class PaymentApiTest {

    private static final String PAYMENTS_PATH = "/api/v1/payments";

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    @DisplayName("내 결제를 최신순 페이지로 주고, 실패 결제는 실패 사유가 있고 결제 시각이 null이다")
    void getPayments() {
        paymentRepository.save(Payment.complete(101L, WALLET_ID, MEMBER_ID,
            List.of(PaymentItem.of(1001L, 10_000L)), NOW));
        paymentRepository.save(Payment.complete(102L, WALLET_ID, 2L, List.of(PaymentItem.of(1002L, 10_000L)), NOW));
        Payment failed = paymentRepository.save(Payment.fail(103L, WALLET_ID, MEMBER_ID, 30_000L,
            PaymentFailureCode.INSUFFICIENT_BALANCE));

        get(MEMBER_ID, PAYMENTS_PATH + "?size=1")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(1)
            .jsonPath("$.content[0].paymentId").isEqualTo(failed.getId())
            .jsonPath("$.content[0].orderId").isEqualTo(103)
            .jsonPath("$.content[0].amount").isEqualTo(30_000)
            .jsonPath("$.content[0].refundedAmount").isEqualTo(0)
            .jsonPath("$.content[0].status").isEqualTo("FAILED")
            .jsonPath("$.content[0].failureCode").isEqualTo("INSUFFICIENT_BALANCE")
            .jsonPath("$.content[0].paidAt").isEmpty()
            .jsonPath("$.content[0].createdAt").isNotEmpty()
            .jsonPath("$.page").isEqualTo(0)
            .jsonPath("$.size").isEqualTo(1)
            .jsonPath("$.totalElements").isEqualTo(2)
            .jsonPath("$.totalPages").isEqualTo(2)
            .jsonPath("$.hasNext").isEqualTo(true);
    }

    @Test
    @DisplayName("결제가 없으면 빈 목록이다")
    void emptyPayments() {
        get(MEMBER_ID, PAYMENTS_PATH)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content").isArray()
            .jsonPath("$.content.length()").isEqualTo(0)
            .jsonPath("$.totalElements").isEqualTo(0);
    }

    @Test
    @DisplayName("size가 100을 넘으면 400")
    void tooLargePageSize() {
        get(MEMBER_ID, PAYMENTS_PATH + "?size=101")
            .expectStatus().isBadRequest()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    @DisplayName("내 결제 상세는 주문상품별 금액 · 환불액을 준다")
    void getPayment() {
        Payment payment = Payment.complete(101L, WALLET_ID, MEMBER_ID, List.of(
            PaymentItem.of(1001L, 10_000L),
            PaymentItem.of(1002L, 5_000L)), NOW);
        payment.refund(1001L, 4_000L);
        Payment saved = paymentRepository.save(payment);

        get(MEMBER_ID, PAYMENTS_PATH + "/" + saved.getId())
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.paymentId").isEqualTo(saved.getId())
            .jsonPath("$.amount").isEqualTo(15_000)
            .jsonPath("$.refundedAmount").isEqualTo(4_000)
            .jsonPath("$.status").isEqualTo("PARTIALLY_REFUNDED")
            .jsonPath("$.failureCode").isEmpty()
            .jsonPath("$.paidAt").isNotEmpty()
            .jsonPath("$.items.length()").isEqualTo(2)
            .jsonPath("$.items[0].orderItemId").isEqualTo(1001)
            .jsonPath("$.items[0].amount").isEqualTo(10_000)
            .jsonPath("$.items[0].refundedAmount").isEqualTo(4_000)
            .jsonPath("$.items[1].orderItemId").isEqualTo(1002)
            .jsonPath("$.items[1].refundedAmount").isEqualTo(0);
    }

    @Test
    @DisplayName("실패 결제 상세의 주문상품은 빈 목록이다")
    void failedPaymentDetail() {
        Payment failed = paymentRepository.save(Payment.fail(101L, WALLET_ID, MEMBER_ID, 30_000L,
            PaymentFailureCode.INSUFFICIENT_BALANCE));

        get(MEMBER_ID, PAYMENTS_PATH + "/" + failed.getId())
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("FAILED")
            .jsonPath("$.items").isArray()
            .jsonPath("$.items.length()").isEqualTo(0);
    }

    @Test
    @DisplayName("남의 결제 상세는 404 PAYMENT_NOT_FOUND")
    void othersPayment() {
        Payment others = paymentRepository.save(Payment.complete(101L, WALLET_ID, 2L,
            List.of(PaymentItem.of(1001L, 10_000L)), NOW));

        get(MEMBER_ID, PAYMENTS_PATH + "/" + others.getId())
            .expectStatus().isNotFound()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.title").isEqualTo("결제를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("로그인하지 않으면 401")
    void withoutToken() {
        client.get().uri(PAYMENTS_PATH)
            .exchange()
            .expectStatus().isUnauthorized();
    }

    private RestTestClient.ResponseSpec get(Long memberId, String uri) {
        return client.get().uri(uri)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(memberId)))
            .exchange();
    }
}
