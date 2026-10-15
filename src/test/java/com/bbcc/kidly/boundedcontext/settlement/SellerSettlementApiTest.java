package com.bbcc.kidly.boundedcontext.settlement;

import com.bbcc.kidly.boundedcontext.settlement.domain.Settlement;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementCreateInfo;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRepository;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class SellerSettlementApiTest {

    private static final String TARGETS_PATH = "/api/v1/seller/settlement-targets";
    private static final String SETTLEMENTS_PATH = "/api/v1/seller/settlements";
    private static final Long SELLER_ID = 10L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Autowired
    private SettlementItemRepository settlementItemRepository;

    @Autowired
    private SettlementRepository settlementRepository;

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

    @Test
    @DisplayName("판매자는 본인의 정산서 목록을 조회한다")
    void settlements() {
        settlementRepository.save(settlement(SELLER_ID));
        settlementRepository.save(settlement(11L));

        client.get().uri(SETTLEMENTS_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.seller(SELLER_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(1)
            .jsonPath("$.content[0].periodStartOn").isEqualTo("2026-10-01")
            .jsonPath("$.content[0].periodEndOn").isEqualTo("2026-10-31")
            .jsonPath("$.content[0].totalSalesAmount").isEqualTo(100_000)
            .jsonPath("$.content[0].feeAmount").isEqualTo(10_000)
            .jsonPath("$.content[0].settlementAmount").isEqualTo(90_000)
            .jsonPath("$.content[0].status").isEqualTo("COMPLETED")
            .jsonPath("$.content[0].paidAt").isEmpty()
            .jsonPath("$.totalElements").isEqualTo(1);
    }

    @Test
    @DisplayName("정산서가 없으면 빈 목록이다")
    void settlementsEmpty() {
        client.get().uri(SETTLEMENTS_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.seller(SELLER_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content").isEmpty();
    }

    @Test
    @DisplayName("상태 필터가 잘못되면 400이다")
    void settlementsInvalidStatus() {
        client.get().uri(SETTLEMENTS_PATH + "?status=WRONG")
            .headers(headers -> headers.setBearerAuth(tokens.seller(SELLER_ID)))
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("구매자는 정산서를 조회할 수 없다. 403이다")
    void settlementsForbidden() {
        client.get().uri(SETTLEMENTS_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(SELLER_ID)))
            .exchange()
            .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("정산서 상세는 포함된 주문상품을 함께 준다")
    void settlementDetail() {
        var settlement = settlementRepository.save(settlement(SELLER_ID));
        settle(settlement.getId(), 301L, 60_000L);
        settle(settlement.getId(), 302L, 40_000L);

        client.get().uri(SETTLEMENTS_PATH + "/" + settlement.getId())
            .headers(headers -> headers.setBearerAuth(tokens.seller(SELLER_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.settlementId").isEqualTo(settlement.getId())
            .jsonPath("$.status").isEqualTo("COMPLETED")
            .jsonPath("$.failReason").isEmpty()
            .jsonPath("$.items.length()").isEqualTo(2)
            .jsonPath("$.items[0].orderItemId").isEqualTo(301)
            .jsonPath("$.items[0].salesAmount").isEqualTo(60_000)
            .jsonPath("$.items[1].orderItemId").isEqualTo(302);
    }

    @Test
    @DisplayName("다른 판매자의 정산서 상세는 404다")
    void settlementDetailOfOther() {
        var settlement = settlementRepository.save(settlement(11L));

        client.get().uri(SETTLEMENTS_PATH + "/" + settlement.getId())
            .headers(headers -> headers.setBearerAuth(tokens.seller(SELLER_ID)))
            .exchange()
            .expectStatus().isNotFound();
    }

    private Settlement settlement(Long sellerId) {
        return Settlement.create(
            new SettlementCreateInfo(
                sellerId, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 100_000L, new BigDecimal("0.1000")),
            SettlementFixture.NOW);
    }

    private void settle(Long settlementId, Long orderItemId, long salesAmount) {
        var item = SettlementFixture.item(orderItemId, SELLER_ID, salesAmount, SettlementFixture.CONFIRMED_AT);
        item.settle(settlementId);
        settlementItemRepository.save(item);
    }
}
