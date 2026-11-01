package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.NOW;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.WALLET_ID;

import com.bbcc.kidly.boundedcontext.payment.domain.Payment;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentItem;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentRepository;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.shared.payment.domain.PaymentFailureCode;
import com.bbcc.kidly.support.FunctionalTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class AdminPaymentApiTest {

    private static final String ADMIN_PAYMENTS_PATH = "/api/v1/admin/payments";
    private static final Long ADMIN_ID = 3L;
    private static final Long OTHER_MEMBER_ID = 2L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    @DisplayName("모든 회원의 결제를 최신순 페이지로 주고, 줄마다 결제한 회원 · 환불액 · 상태를 담는다")
    void getAllPayments() {
        Payment refunded = Payment.complete(101L, WALLET_ID, MEMBER_ID, List.of(PaymentItem.of(1001L, 10_000L)), NOW);
        refunded.refund(1001L, 10_000L);
        paymentRepository.save(refunded);
        paymentRepository.save(Payment.complete(102L, WALLET_ID, OTHER_MEMBER_ID,
            List.of(PaymentItem.of(1002L, 20_000L)), NOW));
        Payment failed = paymentRepository.save(Payment.fail(103L, WALLET_ID, MEMBER_ID, 30_000L,
            PaymentFailureCode.INSUFFICIENT_BALANCE));

        getAsAdmin(ADMIN_PAYMENTS_PATH + "?size=2")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(2)
            .jsonPath("$.content[0].paymentId").isEqualTo(failed.getId())
            .jsonPath("$.content[0].memberId").isEqualTo(MEMBER_ID)
            .jsonPath("$.content[0].orderId").isEqualTo(103)
            .jsonPath("$.content[0].status").isEqualTo("FAILED")
            .jsonPath("$.content[0].failureCode").isEqualTo("INSUFFICIENT_BALANCE")
            .jsonPath("$.content[0].paidAt").isEmpty()
            .jsonPath("$.content[0].createdAt").isNotEmpty()
            .jsonPath("$.content[1].memberId").isEqualTo(OTHER_MEMBER_ID)
            .jsonPath("$.content[1].amount").isEqualTo(20_000)
            .jsonPath("$.content[1].status").isEqualTo("COMPLETED")
            .jsonPath("$.totalElements").isEqualTo(3)
            .jsonPath("$.totalPages").isEqualTo(2)
            .jsonPath("$.hasNext").isEqualTo(true);

        getAsAdmin(ADMIN_PAYMENTS_PATH + "?page=1&size=2")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[0].orderId").isEqualTo(101)
            .jsonPath("$.content[0].refundedAmount").isEqualTo(10_000)
            .jsonPath("$.content[0].status").isEqualTo("REFUNDED");
    }

    @Test
    @DisplayName("결제가 없으면 빈 목록이다")
    void emptyPayments() {
        getAsAdmin(ADMIN_PAYMENTS_PATH)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content").isArray()
            .jsonPath("$.content.length()").isEqualTo(0)
            .jsonPath("$.totalElements").isEqualTo(0);
    }

    @Test
    @DisplayName("size가 100을 넘으면 400")
    void tooLargePageSize() {
        getAsAdmin(ADMIN_PAYMENTS_PATH + "?size=101")
            .expectStatus().isBadRequest()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    private RestTestClient.ResponseSpec getAsAdmin(String uri) {
        return client.get().uri(uri)
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange();
    }
}
