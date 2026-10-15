package com.bbcc.kidly.boundedcontext.settlement;

import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.CONFIRMED_AT;
import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.CUTOFF_AT;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementSellerQuery;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemRepository;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class SettlementSellerQueryIntegrationTest {

    @Autowired
    private SettlementItemRepository settlementItemRepository;

    @Autowired
    private SettlementSellerQuery settlementSellerQuery;

    @BeforeEach
    void setUp() {
        settlementItemRepository.save(SettlementFixture.item(201L, 10L, 10_000L, CONFIRMED_AT));
        settlementItemRepository.save(SettlementFixture.item(202L, 10L, 5_000L, CONFIRMED_AT));
        settlementItemRepository.save(SettlementFixture.item(203L, 11L, 10_000L, CONFIRMED_AT));
        settlementItemRepository.save(SettlementFixture.item(204L, 12L, 10_000L, CUTOFF_AT));
        var settled = SettlementFixture.item(205L, 13L, 10_000L, CONFIRMED_AT);
        settled.settle(7L);
        settlementItemRepository.save(settled);
    }

    @Test
    @DisplayName("기준 전에 확정된 대기 항목이 있는 판매자를 ID 오름차순으로 중복 없이 찾는다")
    void findSellerIds() {
        assertThat(settlementSellerQuery.findSellerIdsToSettle(CUTOFF_AT, 0L, 10)).containsExactly(10L, 11L);
    }

    @Test
    @DisplayName("지정한 판매자 ID보다 큰 판매자만 찾는다")
    void findAfterSellerId() {
        assertThat(settlementSellerQuery.findSellerIdsToSettle(CUTOFF_AT, 10L, 10)).containsExactly(11L);
    }

    @Test
    @DisplayName("limit만큼만 찾는다")
    void findWithLimit() {
        assertThat(settlementSellerQuery.findSellerIdsToSettle(CUTOFF_AT, 0L, 1)).containsExactly(10L);
    }
}
