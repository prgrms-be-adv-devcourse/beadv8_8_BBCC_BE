package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.NOW;

import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.support.FunctionalTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange();
    }
}
