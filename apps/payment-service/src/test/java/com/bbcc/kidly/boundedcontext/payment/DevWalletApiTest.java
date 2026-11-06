package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.MEMBER_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.support.FunctionalTest;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class DevWalletApiTest {

    private static final String DEV_CHARGES_PATH = "/api/v1/dev/wallets/me/charges";

    @Autowired
    private RestTestClient client;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("지갑이 없어도 만들어 넣고, 200과 늘어난 잔액을 주며 원장에 DEV_CHARGE가 남는다")
    void chargeWithoutWallet() {
        charge(MEMBER_ID, Map.of("amount", 50_000))
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.devChargeId").isNotEmpty()
            .jsonPath("$.ledgerId").isNotEmpty()
            .jsonPath("$.amount").isEqualTo(50_000)
            .jsonPath("$.balance").isEqualTo(50_000);

        client.get().uri("/api/v1/wallets/me/ledgers")
            .headers(TestMembers.buyer(MEMBER_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(1)
            .jsonPath("$.content[0].type").isEqualTo("DEV_CHARGE")
            .jsonPath("$.content[0].amount").isEqualTo(50_000)
            .jsonPath("$.content[0].balanceAfter").isEqualTo(50_000);
    }

    @Test
    @DisplayName("있는 잔액에 더하고, 잔액은 원장 합과 같으며 토스 충전 기록은 만들지 않는다")
    void chargeAddsToBalance() {
        walletRepository.save(PaymentFixture.wallet(0L));
        charge(MEMBER_ID, Map.of("amount", 10_000)).expectStatus().isOk();

        charge(MEMBER_ID, Map.of("amount", 1))
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.balance").isEqualTo(10_001);

        assertThat(walletRepository.findByMemberId(MEMBER_ID)).get()
            .extracting(Wallet::getBalance).isEqualTo(10_001L);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT sum(l.amount) FROM payment.wallet_ledger l JOIN payment.wallet w ON w.id = l.wallet_id
            WHERE w.member_id = ?
            """, Long.class, MEMBER_ID)).isEqualTo(10_001L);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM payment.charge", Long.class)).isZero();
    }

    @Test
    @DisplayName("100만 원까지 넣을 수 있다")
    void chargeMaxAmount() {
        charge(MEMBER_ID, Map.of("amount", 1_000_000))
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.balance").isEqualTo(1_000_000);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, 1_000_001})
    @DisplayName("1원보다 적거나 100만 원보다 많으면 400 ProblemDetail이고 잔액은 그대로다")
    void amountOutOfRange(long amount) {
        charge(MEMBER_ID, Map.of("amount", amount))
            .expectStatus().isBadRequest()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);

        assertThat(walletRepository.findByMemberId(MEMBER_ID)).isEmpty();
    }

    @Test
    @DisplayName("금액이 없으면 400 ProblemDetail")
    void missingAmount() {
        charge(MEMBER_ID, Map.of())
            .expectStatus().isBadRequest()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    @DisplayName("탈퇴로 동결된 지갑이면 409 WALLET_FROZEN이고 잔액은 그대로다")
    void frozenWallet() {
        Wallet wallet = PaymentFixture.wallet(3_000L);
        wallet.freeze(LocalDateTime.now());
        walletRepository.save(wallet);

        charge(MEMBER_ID, Map.of("amount", 10_000))
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.title").isEqualTo("동결된 지갑입니다");

        assertThat(walletRepository.findByMemberId(MEMBER_ID)).get()
            .extracting(Wallet::getBalance).isEqualTo(3_000L);
    }

    @Test
    @DisplayName("로그인하지 않으면 401")
    void withoutLogin() {
        client.post().uri(DEV_CHARGES_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("amount", 10_000))
            .exchange()
            .expectStatus().isUnauthorized();
    }

    private RestTestClient.ResponseSpec charge(Long memberId, Map<String, ?> body) {
        return client.post().uri(DEV_CHARGES_PATH)
            .headers(TestMembers.buyer(memberId))
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange();
    }
}
