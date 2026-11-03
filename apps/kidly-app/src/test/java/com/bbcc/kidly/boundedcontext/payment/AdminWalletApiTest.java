package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.global.security.TestAdmins;
import com.bbcc.kidly.support.FunctionalTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class AdminWalletApiTest {

    private static final String ADMIN_WALLETS_PATH = "/api/v1/admin/wallets";
    private static final Long ADMIN_ID = 3L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("frozen=true&hasBalance=true면 동결됐고 잔액이 남은 지갑만 최신순으로 준다")
    void frozenWithBalance() {
        Wallet frozenWithBalance = walletRepository.save(wallet(11L, 5_000L, true));
        walletRepository.save(wallet(12L, 0L, true));
        walletRepository.save(wallet(13L, 7_000L, false));
        Wallet laterFrozenWithBalance = walletRepository.save(wallet(14L, 1_000L, true));

        getAsAdmin(ADMIN_WALLETS_PATH + "?frozen=true&hasBalance=true")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(2)
            .jsonPath("$.content[0].walletId").isEqualTo(laterFrozenWithBalance.getId())
            .jsonPath("$.content[0].memberId").isEqualTo(14)
            .jsonPath("$.content[0].balance").isEqualTo(1_000)
            .jsonPath("$.content[0].frozenAt").isNotEmpty()
            .jsonPath("$.content[1].walletId").isEqualTo(frozenWithBalance.getId())
            .jsonPath("$.totalElements").isEqualTo(2);
    }

    @Test
    @DisplayName("거르지 않으면 모든 지갑을 주고, 동결되지 않은 지갑은 frozenAt이 비어 있다")
    void allWallets() {
        walletRepository.save(wallet(11L, 5_000L, true));
        walletRepository.save(wallet(12L, 0L, false));

        getAsAdmin(ADMIN_WALLETS_PATH)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(2)
            .jsonPath("$.content[0].memberId").isEqualTo(12)
            .jsonPath("$.content[0].frozenAt").isEmpty()
            .jsonPath("$.content[1].memberId").isEqualTo(11);
    }

    @Test
    @DisplayName("frozen=true면 잔액과 상관없이 동결된 지갑만 준다")
    void frozenOnly() {
        walletRepository.save(wallet(11L, 0L, true));
        walletRepository.save(wallet(12L, 5_000L, false));

        getAsAdmin(ADMIN_WALLETS_PATH + "?frozen=true")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(1)
            .jsonPath("$.content[0].memberId").isEqualTo(11);
    }

    @Test
    @DisplayName("size가 100을 넘으면 400")
    void tooLargePageSize() {
        getAsAdmin(ADMIN_WALLETS_PATH + "?size=101")
            .expectStatus().isBadRequest()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    @DisplayName("동결된 지갑의 잔액을 환급하면 잔액이 0이 되고 환급 기록 · 원장 BALANCE_RETURN이 남는다. 같은 요청을 다시 보내면 409")
    void returnBalance() {
        Wallet wallet = walletRepository.save(wallet(21L, 5_000L, true));

        postReturnBalance(wallet.getId(), 5_000L)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.walletId").isEqualTo(wallet.getId())
            .jsonPath("$.memberId").isEqualTo(21)
            .jsonPath("$.amount").isEqualTo(5_000)
            .jsonPath("$.balance").isEqualTo(0)
            .jsonPath("$.balanceReturnId").isNotEmpty()
            .jsonPath("$.ledgerId").isNotEmpty();
        postReturnBalance(wallet.getId(), 5_000L)
            .expectStatus().isEqualTo(409)
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);

        assertBalance(wallet.getId(), 0L);
        Map<String, Object> ledger = jdbcTemplate.queryForMap(
            "select type, amount, balance_after from payment.wallet_ledger where wallet_id = ?", wallet.getId());
        assertThat(ledger).containsEntry("type", "BALANCE_RETURN")
            .containsEntry("amount", -5_000L)
            .containsEntry("balance_after", 0L);
        assertThat(jdbcTemplate.queryForObject(
            "select returned_by from payment.balance_return where wallet_id = ?", Long.class, wallet.getId()))
            .isEqualTo(ADMIN_ID);
    }

    @Test
    @DisplayName("동결되지 않은 지갑 · 잔액과 다른 금액은 409, 없는 지갑은 404, 0원은 400이고 잔액은 그대로다")
    void returnBalanceRejected() {
        Wallet open = walletRepository.save(wallet(22L, 5_000L, false));
        Wallet frozen = walletRepository.save(wallet(23L, 7_000L, true));

        postReturnBalance(open.getId(), 5_000L).expectStatus().isEqualTo(409);
        postReturnBalance(frozen.getId(), 5_000L).expectStatus().isEqualTo(409);
        postReturnBalance(Long.MAX_VALUE, 5_000L).expectStatus().isNotFound();
        postReturnBalance(frozen.getId(), 0L).expectStatus().isBadRequest();

        assertBalance(open.getId(), 5_000L);
        assertBalance(frozen.getId(), 7_000L);
    }

    private RestTestClient.ResponseSpec postReturnBalance(Long walletId, long amount) {
        return client.post().uri(ADMIN_WALLETS_PATH + "/" + walletId + "/return-balance")
            .headers(TestMembers.admin(ADMIN_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("amount", amount))
            .exchange();
    }

    private void assertBalance(Long walletId, long expected) {
        assertThat(
            jdbcTemplate.queryForObject("select balance from payment.wallet where id = ?", Long.class, walletId))
            .isEqualTo(expected);
    }

    private static Wallet wallet(Long memberId, long balance, boolean frozen) {
        Wallet wallet = Wallet.create(memberId);
        if (balance > 0) {
            wallet.deposit(balance);
        }
        if (frozen) {
            wallet.freeze(NOW);
        }
        return wallet;
    }

    private RestTestClient.ResponseSpec getAsAdmin(String uri) {
        return client.get().uri(uri)
            .headers(TestAdmins.of(ADMIN_ID))
            .exchange();
    }
}
