package com.bbcc.kidly;

import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.support.FunctionalTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 정산 서비스가 정산 도메인만으로 뜨고, 정산 API만 받는지 본다. 정산 도메인 자체의 동작은 도메인 테스트가 본다.
 */
@FunctionalTest
class SettlementServiceApplicationApiTest {

    private static final Long MEMBER_ID = 1L;

    @Autowired
    private RestTestClient client;

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
    @DisplayName("정산 API가 라우팅된다: 판매자는 정산 목록을 조회한다")
    void sellerSettlementsRouted() {
        client.get().uri("/api/v1/seller/settlements")
            .headers(TestMembers.seller(MEMBER_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content").isEmpty();
    }

    @Test
    @DisplayName("다른 도메인 API는 없다(404)")
    void noOtherDomainApi() {
        client.get().uri("/api/v1/products")
            .headers(TestMembers.seller(MEMBER_ID))
            .exchange()
            .expectStatus().isNotFound();
        client.get().uri("/api/v1/children")
            .headers(TestMembers.buyer(MEMBER_ID))
            .exchange()
            .expectStatus().isNotFound();
    }
}
