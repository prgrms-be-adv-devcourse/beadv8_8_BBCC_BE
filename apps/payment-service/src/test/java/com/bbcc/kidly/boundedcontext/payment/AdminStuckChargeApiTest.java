package com.bbcc.kidly.boundedcontext.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.payment.domain.Charge;
import com.bbcc.kidly.boundedcontext.payment.domain.ChargeRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.global.security.TestAdmins;
import com.bbcc.kidly.support.FunctionalTest;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class AdminStuckChargeApiTest {

    private static final String STUCK_PATH = "/api/v1/admin/wallets/charges/stuck";
    private static final Long ADMIN_ID = 3L;
    private static final Long MEMBER_ID = 31L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private ChargeRepository chargeRepository;

    @Autowired
    private FakePaymentGateway fakePaymentGateway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Wallet wallet;

    @BeforeEach
    void setUp() {
        Wallet created = Wallet.create(MEMBER_ID);
        created.deposit(3_000L);
        wallet = walletRepository.save(created);
    }

    @AfterEach
    void resetGateway() {
        fakePaymentGateway.reset();
    }

    @Test
    @DisplayName("멈춘 충전 목록은 기준 시간보다 오래 승인 중인 충전만 오래된 순으로 주고 paymentKey는 주지 않는다")
    void listStuck() {
        Charge newer = confirmingSince(10_000L, 10);
        Charge older = confirmingSince(20_000L, 30);
        confirmingSince(30_000L, 1);
        Charge pending = chargeRepository.save(Charge.request(wallet.getId(), UUID.randomUUID().toString(), 40_000L,
            LocalDateTime.now().plusMinutes(30)));
        setUpdatedAt(pending, 60);

        getAsAdmin(STUCK_PATH)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(2)
            .jsonPath("$.content[0].chargeId").isEqualTo(older.getId())
            .jsonPath("$.content[0].walletId").isEqualTo(wallet.getId())
            .jsonPath("$.content[0].memberId").isEqualTo(MEMBER_ID)
            .jsonPath("$.content[0].tossOrderId").isEqualTo(older.getTossOrderId())
            .jsonPath("$.content[0].amount").isEqualTo(20_000)
            .jsonPath("$.content[0].status").isEqualTo("CONFIRMING")
            .jsonPath("$.content[0].createdAt").isNotEmpty()
            .jsonPath("$.content[0].updatedAt").isNotEmpty()
            .jsonPath("$.content[0].paymentKeyEnc").doesNotExist()
            .jsonPath("$.content[1].chargeId").isEqualTo(newer.getId())
            .jsonPath("$.totalElements").isEqualTo(2);
    }

    @Test
    @DisplayName("size가 100을 넘으면 400")
    void tooLargePageSize() {
        getAsAdmin(STUCK_PATH + "?size=101")
            .expectStatus().isBadRequest()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    @DisplayName("토스 승인 금액이 다른 멈춘 충전을 끝내면 ADMIN_CLOSED 실패가 되고 예치금 · 원장은 그대로다. 다시 끝내면 409")
    void closeAmountMismatch() {
        Charge charge = confirmingSince(10_000L, 10);
        fakePaymentGateway.willFindApproved(charge.getTossOrderId(), 7_000L);

        postFail(charge.getId())
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.chargeId").isEqualTo(charge.getId())
            .jsonPath("$.walletId").isEqualTo(wallet.getId())
            .jsonPath("$.memberId").isEqualTo(MEMBER_ID)
            .jsonPath("$.tossOrderId").isEqualTo(charge.getTossOrderId())
            .jsonPath("$.amount").isEqualTo(10_000)
            .jsonPath("$.status").isEqualTo("FAILED");
        postFail(charge.getId()).expectStatus().isEqualTo(409);

        assertFailedByAdmin(charge);
        assertWalletUntouched();
    }

    @Test
    @DisplayName("토스에서 부분 취소된 멈춘 충전도 끝낼 수 있고 목록에서 빠진다")
    void closePartialCancel() {
        Charge charge = confirmingSince(10_000L, 10);
        fakePaymentGateway.willFindRejected(charge.getTossOrderId(), "PARTIAL_CANCELED");

        postFail(charge.getId()).expectStatus().isOk();

        assertFailedByAdmin(charge);
        assertWalletUntouched();
        getAsAdmin(STUCK_PATH).expectStatus().isOk().expectBody().jsonPath("$.content.length()").isEqualTo(0);
    }

    @Test
    @DisplayName("토스가 충전 금액대로 승인한 충전은 스케줄러가 입금하므로 409이고, 토스 결과를 모르면 503이다. 둘 다 승인 중으로 남는다")
    void refuseWhenTossApprovedOrUnknown() {
        Charge approved = confirmingSince(10_000L, 10);
        Charge unknown = confirmingSince(20_000L, 10);
        fakePaymentGateway.willFindApproved(approved.getTossOrderId(), 10_000L);

        postFail(approved.getId())
            .expectStatus().isEqualTo(409)
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        postFail(unknown.getId()).expectStatus().isEqualTo(503);

        assertThat(statusOf(approved)).isEqualTo("CONFIRMING");
        assertThat(statusOf(unknown)).isEqualTo("CONFIRMING");
        assertWalletUntouched();
    }

    @Test
    @DisplayName("기준 시간 안에 승인을 시작한 충전 · 승인 중이 아닌 충전은 409, 없는 충전은 404")
    void refuseNotStuck() {
        Charge recent = confirmingSince(10_000L, 1);
        Charge pending = chargeRepository.save(Charge.request(wallet.getId(), UUID.randomUUID().toString(), 20_000L,
            LocalDateTime.now().plusMinutes(30)));
        setUpdatedAt(pending, 60);
        fakePaymentGateway.willFindRejected(recent.getTossOrderId(), "PARTIAL_CANCELED");
        fakePaymentGateway.willFindRejected(pending.getTossOrderId(), "PARTIAL_CANCELED");

        postFail(recent.getId()).expectStatus().isEqualTo(409);
        postFail(pending.getId()).expectStatus().isEqualTo(409);
        postFail(Long.MAX_VALUE).expectStatus().isNotFound();

        assertThat(statusOf(recent)).isEqualTo("CONFIRMING");
        assertThat(statusOf(pending)).isEqualTo("PENDING");
    }

    // 승인 중으로 바꾼 지 minutesAgo분 지난 충전. updated_at은 Auditing이 채우므로 저장한 뒤 직접 되돌린다
    private Charge confirmingSince(long amount, int minutesAgo) {
        Charge charge = Charge.request(wallet.getId(), UUID.randomUUID().toString(), amount,
            LocalDateTime.now().plusMinutes(30));
        charge.startConfirming(amount, LocalDateTime.now());
        Charge saved = chargeRepository.save(charge);
        setUpdatedAt(saved, minutesAgo);
        return saved;
    }

    private void setUpdatedAt(Charge charge, int minutesAgo) {
        jdbcTemplate.update("update payment.charge set updated_at = ? where id = ?",
            LocalDateTime.now().minusMinutes(minutesAgo), charge.getId());
    }

    private String statusOf(Charge charge) {
        return jdbcTemplate.queryForObject("select status from payment.charge where id = ?", String.class,
            charge.getId());
    }

    private void assertFailedByAdmin(Charge charge) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
            "select status, failure_code from payment.charge where id = ?", charge.getId());
        assertThat(row).containsEntry("status", "FAILED").containsEntry("failure_code", Charge.ADMIN_CLOSED);
    }

    private void assertWalletUntouched() {
        assertThat(jdbcTemplate.queryForObject("select balance from payment.wallet where id = ?", Long.class,
            wallet.getId())).isEqualTo(3_000L);
        assertThat(jdbcTemplate.queryForObject("select count(*) from payment.wallet_ledger", Long.class)).isZero();
    }

    private RestTestClient.ResponseSpec postFail(Long chargeId) {
        return client.post().uri("/api/v1/admin/wallets/charges/" + chargeId + "/fail")
            .headers(TestAdmins.of(ADMIN_ID))
            .exchange();
    }

    private RestTestClient.ResponseSpec getAsAdmin(String uri) {
        return client.get().uri(uri)
            .headers(TestAdmins.of(ADMIN_ID))
            .exchange();
    }
}
