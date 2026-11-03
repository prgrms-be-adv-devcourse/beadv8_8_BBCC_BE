package com.bbcc.kidly.boundedcontext.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.support.FunctionalTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 결제 서비스가 결제 도메인만으로 뜨고, 결제 API를 kidly-app과 같은 주소 · 같은 응답 형식으로 내는지 본다.
 * API마다의 동작은 kidly-app의 결제 기능 테스트가 확인하고, 여기서는 지갑 조회 하나로 본다.
 */
@FunctionalTest
class PaymentServiceApplicationApiTest {

    @Autowired
    private RestTestClient client;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WalletRepository walletRepository;

    @Test
    @DisplayName("health가 UP이다")
    void healthUp() {
        client.get().uri("/actuator/health")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    @DisplayName("다른 도메인 API는 없어 kidly-app과 같은 ProblemDetail 형식의 404를 받는다")
    void noOtherDomainApi() {
        client.get().uri("/api/v1/orders")
            .headers(TestMembers.buyer(PaymentFixture.MEMBER_ID))
            .exchange()
            .expectStatus().isNotFound()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status").isEqualTo(404)
            .jsonPath("$.title").isNotEmpty()
            .jsonPath("$.instance").isEqualTo("/api/v1/orders");
    }

    // 결제 마이그레이션을 더하고 baseline-version을 올리지 않으면, kidly-app이 이미 적용한 DB에서 다시 적용하려다 기동에 실패한다
    @Value("${spring.flyway.baseline-version}")
    private String baselineVersion;

    @Test
    @DisplayName("빈 DB에는 init부터 적용하고, 마지막 버전은 baseline-version이며, 테이블은 결제 스키마에만 만든다")
    void migratesOnlyPaymentSchema() {
        var schemas = jdbcTemplate.queryForList("""
            SELECT DISTINCT schemaname FROM pg_tables
            WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
            """, String.class);
        // version이 없는 행은 Flyway가 스키마를 만든 기록이다
        var versions = jdbcTemplate.queryForList("""
            SELECT version FROM payment.flyway_schema_history_payment
            WHERE success AND version IS NOT NULL ORDER BY installed_rank
            """, String.class);

        assertThat(schemas).containsExactly("payment");
        assertThat(versions.getFirst()).isEqualTo("202610160256");
        assertThat(versions.getLast()).isEqualTo(baselineVersion);
    }

    @Test
    @DisplayName("내 지갑 API를 kidly-app과 같은 주소로 부르면 같은 형식으로 잔액을 답한다")
    void myWallet() {
        walletRepository.save(PaymentFixture.wallet(15_000L));

        client.get().uri("/api/v1/wallets/me")
            .headers(TestMembers.buyer(PaymentFixture.MEMBER_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.balance").isEqualTo(15_000);
    }

    @Test
    @DisplayName("로그인 헤더가 없으면 401이다")
    void myWalletWithoutLogin() {
        client.get().uri("/api/v1/wallets/me")
            .exchange()
            .expectStatus().isUnauthorized();
    }
}
