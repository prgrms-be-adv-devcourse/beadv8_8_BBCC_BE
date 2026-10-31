package com.bbcc.kidly.boundedcontext.settlement;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRepository;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.support.FunctionalTest;
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

    @Autowired
    private RestTestClient client;

    @Autowired
    private SettlementItemRepository settlementItemRepository;

    @Autowired
    private SettlementRepository settlementRepository;

    @Test
    @DisplayName("판매자는 본인의 정산 대상을 조회한다")
    void targets() {
        settlementItemRepository.save(
            SettlementFixture.item(201L, SettlementFixture.SELLER_ID, 10_000L, LocalDateTime.of(2026, 10, 1, 10, 0)));
        settlementItemRepository.save(
            SettlementFixture.item(202L, 11L, 5_000L, LocalDateTime.of(2026, 10, 1, 11, 0)));

        client.get().uri(TARGETS_PATH)
            .headers(TestMembers.seller(SettlementFixture.SELLER_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(1)
            .jsonPath("$.content[0].orderItemId").isEqualTo(201)
            .jsonPath("$.content[0].orderId").isEqualTo(SettlementFixture.ORDER_ID)
            .jsonPath("$.content[0].salesAmount").isEqualTo(10_000)
            .jsonPath("$.content[0].confirmedAt").isNotEmpty()
            .jsonPath("$.totalElements").isEqualTo(1);
    }

    @Test
    @DisplayName("정산 대상이 없으면 빈 목록이다")
    void targetsEmpty() {
        client.get().uri(TARGETS_PATH)
            .headers(TestMembers.seller(SettlementFixture.SELLER_ID))
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
            .headers(TestMembers.buyer(SettlementFixture.SELLER_ID))
            .exchange()
            .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("판매자는 본인의 정산서 목록을 조회한다")
    void settlements() {
        settlementRepository.save(SettlementFixture.settlement(
            SettlementFixture.SELLER_ID, SettlementFixture.PERIOD_START, SettlementFixture.PERIOD_END));
        settlementRepository.save(SettlementFixture.settlement(
            11L, SettlementFixture.PERIOD_START, SettlementFixture.PERIOD_END));

        client.get().uri(SETTLEMENTS_PATH)
            .headers(TestMembers.seller(SettlementFixture.SELLER_ID))
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
    @DisplayName("상태와 종료일 범위로 정산서를 거른다")
    void settlementsFiltered() {
        settlementRepository.save(
            SettlementFixture.settlement(
                SettlementFixture.SELLER_ID, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));
        var paid = SettlementFixture.settlement(
            SettlementFixture.SELLER_ID, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        paid.markPaid(SettlementFixture.NOW);
        settlementRepository.save(paid);
        settlementRepository.save(SettlementFixture.settlement(
            SettlementFixture.SELLER_ID, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));

        client.get().uri(SETTLEMENTS_PATH + "?status=PAID&fromOn=2026-09-01&toOn=2026-09-30")
            .headers(TestMembers.seller(SettlementFixture.SELLER_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(1)
            .jsonPath("$.content[0].periodEndOn").isEqualTo("2026-09-30")
            .jsonPath("$.content[0].status").isEqualTo("PAID")
            .jsonPath("$.totalElements").isEqualTo(1);
    }

    @Test
    @DisplayName("정산서가 없으면 빈 목록이다")
    void settlementsEmpty() {
        client.get().uri(SETTLEMENTS_PATH)
            .headers(TestMembers.seller(SettlementFixture.SELLER_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content").isEmpty();
    }

    @Test
    @DisplayName("상태 필터가 잘못되면 400이다")
    void settlementsInvalidStatus() {
        client.get().uri(SETTLEMENTS_PATH + "?status=WRONG")
            .headers(TestMembers.seller(SettlementFixture.SELLER_ID))
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("구매자는 정산서를 조회할 수 없다. 403이다")
    void settlementsForbidden() {
        client.get().uri(SETTLEMENTS_PATH)
            .headers(TestMembers.buyer(SettlementFixture.SELLER_ID))
            .exchange()
            .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("정산서 상세는 포함된 주문상품을 함께 준다")
    void settlementDetail() {
        var settlement = settlementRepository.save(SettlementFixture.settlement(
            SettlementFixture.SELLER_ID, SettlementFixture.PERIOD_START, SettlementFixture.PERIOD_END));
        settle(settlement.getId(), 301L, 60_000L);
        settle(settlement.getId(), 302L, 40_000L);

        client.get().uri(SETTLEMENTS_PATH + "/" + settlement.getId())
            .headers(TestMembers.seller(SettlementFixture.SELLER_ID))
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
        var settlement = settlementRepository.save(SettlementFixture.settlement(
            11L, SettlementFixture.PERIOD_START, SettlementFixture.PERIOD_END));

        client.get().uri(SETTLEMENTS_PATH + "/" + settlement.getId())
            .headers(TestMembers.seller(SettlementFixture.SELLER_ID))
            .exchange()
            .expectStatus().isNotFound();
    }

    private void settle(Long settlementId, Long orderItemId, long salesAmount) {
        var item = SettlementFixture.item(
            orderItemId, SettlementFixture.SELLER_ID, salesAmount, SettlementFixture.CONFIRMED_AT);
        item.settle(settlementId);
        settlementItemRepository.save(item);
    }
}
