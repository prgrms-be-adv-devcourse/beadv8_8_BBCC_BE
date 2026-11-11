package com.bbcc.kidly.boundedcontext.settlement;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRepository;
import com.bbcc.kidly.support.FunctionalTest;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class InternalSellerSettlementApiTest {

    private static final String BLOCKERS_PATH = "/internal/sellers/{sellerId}/withdrawal-blockers";

    @Autowired
    private RestTestClient client;

    @Autowired
    private SettlementItemRepository settlementItemRepository;

    @Autowired
    private SettlementRepository settlementRepository;

    @Test
    @DisplayName("미정산 항목과 지급되지 않은(지급 대기 · 실패) 정산서를 탈퇴 차단 사유로 센다")
    void withdrawalBlockers() {
        var sellerId = SettlementFixture.SELLER_ID;
        savePendingItem(401L, sellerId);
        savePendingItem(402L, sellerId);
        savePendingItem(403L, 11L);
        var completed = settlementRepository.save(
            SettlementFixture.settlement(sellerId, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));
        var settled = SettlementFixture.item(404L, sellerId, 10_000L, SettlementFixture.CONFIRMED_AT);
        settled.settle(completed.getId());
        settlementItemRepository.save(settled);
        var failed = SettlementFixture.settlement(sellerId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        failed.markPaymentFailed("ACCOUNT_CLOSED");
        settlementRepository.save(failed);
        var paid = SettlementFixture.settlement(sellerId, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        paid.markPaid(SettlementFixture.NOW);
        settlementRepository.save(paid);

        client.get().uri(BLOCKERS_PATH, sellerId)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .json("""
                {"blockers":[{"type":"UNSETTLED_ITEM","count":2},{"type":"UNPAID_SETTLEMENT","count":2}]}
                """, JsonCompareMode.STRICT);
    }

    @Test
    @DisplayName("막을 사유가 없거나 모르는 판매자면 빈 목록이다")
    void noWithdrawalBlockers() {
        client.get().uri(BLOCKERS_PATH, 999L)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .json("""
                {"blockers":[]}
                """, JsonCompareMode.STRICT);
    }

    private void savePendingItem(Long orderItemId, Long sellerId) {
        settlementItemRepository.save(
            SettlementFixture.item(orderItemId, sellerId, 10_000L, SettlementFixture.CONFIRMED_AT));
    }
}
