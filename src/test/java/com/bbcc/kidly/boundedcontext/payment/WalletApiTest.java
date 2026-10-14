package com.bbcc.kidly.boundedcontext.payment;

import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedger;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerType;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class WalletApiTest {

    private static final String MY_WALLET_PATH = "/api/v1/wallets/me";
    private static final String LEDGERS_PATH = MY_WALLET_PATH + "/ledgers";

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletLedgerRepository walletLedgerRepository;

    @Test
    @DisplayName("내 지갑 잔액을 준다")
    void getBalance() {
        walletRepository.save(PaymentFixture.wallet(15_000L));

        client.get().uri(MY_WALLET_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(PaymentFixture.MEMBER_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.balance").isEqualTo(15_000);
    }

    @Test
    @DisplayName("지갑이 없으면 404")
    void walletNotFound() {
        client.get().uri(MY_WALLET_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(PaymentFixture.MEMBER_ID)))
            .exchange()
            .expectStatus().isNotFound()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.title").isEqualTo("지갑을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("로그인하지 않으면 401")
    void withoutToken() {
        client.get().uri(MY_WALLET_PATH)
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("원장은 내 지갑의 거래를 최신순 페이지로 주고, 입금은 양수 · 출금은 음수다")
    void getLedgers() {
        Wallet wallet = walletRepository.save(PaymentFixture.wallet(0L));
        Wallet other = walletRepository.save(Wallet.create(2L));
        walletLedgerRepository.save(WalletLedger.record(wallet.getId(), WalletLedgerType.CHARGE, 10_000L, 10_000L, 1L));
        walletLedgerRepository.save(WalletLedger.record(other.getId(), WalletLedgerType.CHARGE, 5_000L, 5_000L, 2L));
        WalletLedger payment = walletLedgerRepository.save(
            WalletLedger.record(wallet.getId(), WalletLedgerType.PAYMENT, 3_000L, 7_000L, 100L));

        getLedgers("?size=1")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(1)
            .jsonPath("$.content[0].ledgerId").isEqualTo(payment.getId())
            .jsonPath("$.content[0].type").isEqualTo("PAYMENT")
            .jsonPath("$.content[0].amount").isEqualTo(-3_000)
            .jsonPath("$.content[0].balanceAfter").isEqualTo(7_000)
            .jsonPath("$.content[0].refId").isEqualTo(100)
            .jsonPath("$.content[0].createdAt").isNotEmpty()
            .jsonPath("$.page").isEqualTo(0)
            .jsonPath("$.size").isEqualTo(1)
            .jsonPath("$.totalElements").isEqualTo(2)
            .jsonPath("$.totalPages").isEqualTo(2)
            .jsonPath("$.hasNext").isEqualTo(true);
        getLedgers("?page=1&size=1")
            .expectBody()
            .jsonPath("$.content[0].type").isEqualTo("CHARGE")
            .jsonPath("$.content[0].amount").isEqualTo(10_000)
            .jsonPath("$.hasNext").isEqualTo(false);
    }

    @Test
    @DisplayName("원장이 없으면 빈 목록이다")
    void emptyLedgers() {
        walletRepository.save(PaymentFixture.wallet(0L));

        getLedgers("")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content").isArray()
            .jsonPath("$.content.length()").isEqualTo(0)
            .jsonPath("$.totalElements").isEqualTo(0);
    }

    @Test
    @DisplayName("원장의 size가 100을 넘으면 400")
    void tooLargePageSize() {
        walletRepository.save(PaymentFixture.wallet(0L));

        getLedgers("?size=101")
            .expectStatus().isBadRequest()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    @DisplayName("지갑이 없으면 원장도 404")
    void ledgersWithoutWallet() {
        getLedgers("")
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.title").isEqualTo("지갑을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("로그인하지 않으면 원장도 401")
    void ledgersWithoutToken() {
        client.get().uri(LEDGERS_PATH)
            .exchange()
            .expectStatus().isUnauthorized();
    }

    private RestTestClient.ResponseSpec getLedgers(String query) {
        return client.get().uri(LEDGERS_PATH + query)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(PaymentFixture.MEMBER_ID)))
            .exchange();
    }
}
