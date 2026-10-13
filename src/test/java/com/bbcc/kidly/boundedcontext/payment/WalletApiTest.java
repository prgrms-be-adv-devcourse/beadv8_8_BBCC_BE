package com.bbcc.kidly.boundedcontext.payment;

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

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Autowired
    private WalletRepository walletRepository;

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
}
