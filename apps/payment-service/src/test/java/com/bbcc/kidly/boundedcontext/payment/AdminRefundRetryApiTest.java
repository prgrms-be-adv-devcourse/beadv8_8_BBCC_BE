package com.bbcc.kidly.boundedcontext.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.payment.app.OrderItemCancellationService;
import com.bbcc.kidly.boundedcontext.payment.app.dto.OrderItemCancelCommand;
import com.bbcc.kidly.boundedcontext.payment.domain.Payment;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentItem;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.global.security.TestAdmins;
import com.bbcc.kidly.support.FunctionalTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 환불하지 못한 주문상품 취소는 재처리 기록으로 남고, 관리자가 다시 처리하거나 닫는다.
 */
@FunctionalTest
class AdminRefundRetryApiTest {

    private static final String PATH = "/api/v1/admin/payments/refund-retries";
    private static final Long ADMIN_ID = 3L;
    private static final Long MEMBER_ID = 41L;
    private static final Long ORDER_ID = 410L;
    private static final Long ORDER_ITEM_ID = 4100L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private OrderItemCancellationService cancellationService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("환불할 수 없는 부분 취소는 실패한 환불과 재처리 기록으로 남고, 원인을 고친 뒤 다시 처리하면 그 환불이 완료되고 기록이 지워진다")
    void retryAfterFix() {
        Wallet wallet = walletRepository.save(Wallet.create(MEMBER_ID));
        paymentRepository.save(Payment.complete(ORDER_ID, wallet.getId(), MEMBER_ID,
            List.of(PaymentItem.of(ORDER_ITEM_ID, 3_000L)), LocalDateTime.now()));
        cancellationService.handle(command(71L, 5_000L));

        Long retryId = jdbcTemplate.queryForObject(
            "select id from payment.order_item_cancellation_retry where cancellation_id = 71", Long.class);
        client.get().uri(PATH).headers(TestAdmins.of(ADMIN_ID)).exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(1)
            .jsonPath("$.content[0].retryId").isEqualTo(retryId)
            .jsonPath("$.content[0].orderId").isEqualTo(ORDER_ID)
            .jsonPath("$.content[0].items[0].orderItemId").isEqualTo(ORDER_ITEM_ID)
            .jsonPath("$.content[0].amount").isEqualTo(5_000)
            .jsonPath("$.content[0].lastError").isEqualTo("REFUND_AMOUNT_EXCEEDED");

        retry(retryId).expectStatus().isOk()
            .expectBody()
            .jsonPath("$.resolved").isEqualTo(false)
            .jsonPath("$.retry.attemptCount").isEqualTo(2);

        jdbcTemplate.update("update payment.payment_item set amount = 5000 where order_item_id = ?", ORDER_ITEM_ID);
        jdbcTemplate.update("update payment.payment set amount = 5000 where order_id = ?", ORDER_ID);
        retry(retryId).expectStatus().isOk()
            .expectBody()
            .jsonPath("$.resolved").isEqualTo(true)
            .jsonPath("$.retry").isEmpty();

        Map<String, Object> refund = jdbcTemplate.queryForMap(
            "select id, status, failure_code, amount from payment.refund where cancellation_id = 71");
        assertThat(refund).containsEntry("status", "COMPLETED").containsEntry("failure_code", null)
            .containsEntry("amount", 5_000L);
        assertThat(jdbcTemplate.queryForObject("select count(*) from payment.refund_item where refund_id = ?",
            Long.class, refund.get("id"))).isOne();
        assertThat(jdbcTemplate.queryForObject("select balance from payment.wallet where id = ?", Long.class,
            wallet.getId())).isEqualTo(5_000L);
        assertThat(jdbcTemplate.queryForObject("select count(*) from payment.order_item_cancellation_retry",
            Long.class)).isZero();
        retry(retryId).expectStatus().isNotFound();
    }

    @Test
    @DisplayName("결제가 없는 부분 취소도 예외 없이 재처리 기록으로 남고, 관리자가 닫으면 기록만 지운다")
    void closePaymentMissing() {
        cancellationService.handle(command(72L, 5_000L));
        Long retryId = jdbcTemplate.queryForObject(
            "select id from payment.order_item_cancellation_retry where cancellation_id = 72", Long.class);

        client.get().uri(PATH).headers(TestAdmins.of(ADMIN_ID)).exchange()
            .expectBody().jsonPath("$.content[0].lastError").isEqualTo("PAYMENT_NOT_FOUND");

        client.delete().uri(PATH + "/" + retryId).headers(TestAdmins.of(ADMIN_ID)).exchange()
            .expectStatus().isNoContent();
        assertThat(jdbcTemplate.queryForObject("select count(*) from payment.order_item_cancellation_retry",
            Long.class)).isZero();
        client.delete().uri(PATH + "/" + retryId).headers(TestAdmins.of(ADMIN_ID)).exchange()
            .expectStatus().isNotFound();
    }

    private RestTestClient.ResponseSpec retry(Long retryId) {
        return client.post().uri(PATH + "/" + retryId + "/retry").headers(TestAdmins.of(ADMIN_ID)).exchange();
    }

    private static OrderItemCancelCommand command(Long cancellationId, long amount) {
        return new OrderItemCancelCommand(ORDER_ID, MEMBER_ID, cancellationId, "OUT_OF_STOCK",
            List.of(new OrderItemCancelCommand.Item(ORDER_ITEM_ID, amount)));
    }
}
