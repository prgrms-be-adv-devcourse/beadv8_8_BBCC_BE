package com.bbcc.kidly.boundedcontext.settlement;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemRepository;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class SellerSettlementApiTest {

    private static final String TARGETS_PATH = "/api/v1/seller/settlement-targets";
    private static final Long SELLER_ID = 10L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Autowired
    private SettlementItemRepository settlementItemRepository;

    @Test
    @DisplayName("판매자는 본인의 정산 대상을 조회한다")
    void targets() {
        settlementItemRepository.save(
            SettlementFixture.item(201L, SELLER_ID, 10_000L, LocalDateTime.of(2026, 10, 1, 10, 0)));
        settlementItemRepository.save(
            SettlementFixture.item(202L, 11L, 5_000L, LocalDateTime.of(2026, 10, 1, 11, 0)));

        client.get().uri(TARGETS_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.seller(SELLER_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(1)
            .jsonPath("$.content[0].orderItemId").isEqualTo(201)
            .jsonPath("$.content[0].orderId").isEqualTo(1)
            .jsonPath("$.content[0].salesAmount").isEqualTo(10_000)
            .jsonPath("$.content[0].confirmedAt").isNotEmpty()
            .jsonPath("$.totalElements").isEqualTo(1);
    }

    @Test
    @DisplayName("정산 대상이 없으면 빈 목록이다")
    void targetsEmpty() {
        client.get().uri(TARGETS_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.seller(SELLER_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content").isEmpty()
            .jsonPath("$.totalElements").isEqualTo(0);
    }

    @Test
    @DisplayName("구매자는 조회할 수 없다. 403이다")
    void targetsForbidden() {
        client.get().uri(TARGETS_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(SELLER_ID)))
            .exchange()
            .expectStatus().isForbidden();
    }
}
