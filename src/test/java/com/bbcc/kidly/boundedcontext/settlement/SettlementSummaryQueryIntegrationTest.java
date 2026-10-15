package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementSearch;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementSummaryQuery;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementStatus;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

@IntegrationTest
class SettlementSummaryQueryIntegrationTest {

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private SettlementSummaryQuery settlementSummaryQuery;

    @BeforeEach
    void setUp() {
        settlementRepository.save(SettlementFixture.settlement(
            SettlementFixture.SELLER_ID, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));
        var paid = SettlementFixture.settlement(
            SettlementFixture.SELLER_ID, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        paid.markPaid(SettlementFixture.NOW);
        settlementRepository.save(paid);
        var failed = SettlementFixture.settlement(
            SettlementFixture.SELLER_ID, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        failed.markPaymentFailed("계좌 오류");
        settlementRepository.save(failed);
        settlementRepository.save(SettlementFixture.settlement(
            11L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));
    }

    @Test
    @DisplayName("본인의 정산서만 종료일 역순으로 찾는다")
    void findAll() {
        var page = settlementSummaryQuery.findAll(search(null, null, null), PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting("periodEndOn")
            .containsExactly(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 9, 30), LocalDate.of(2026, 8, 31));
        assertThat(page.getContent()).extracting("status").containsExactly("FAILED", "PAID", "COMPLETED");
        assertThat(page.getTotalElements()).isEqualTo(3);
        var first = page.getContent().getFirst();
        assertThat(first.totalSalesAmount()).isEqualTo(100_000L);
        assertThat(first.feeAmount()).isEqualTo(10_000L);
        assertThat(first.settlementAmount()).isEqualTo(90_000L);
        assertThat(first.paidAt()).isNull();
    }

    @Test
    @DisplayName("상태로 거른다")
    void byStatus() {
        var page = settlementSummaryQuery.findAll(search(SettlementStatus.PAID, null, null), PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting("periodEndOn").containsExactly(LocalDate.of(2026, 9, 30));
        assertThat(page.getContent().getFirst().paidAt()).isEqualTo(SettlementFixture.NOW);
    }

    @Test
    @DisplayName("종료일 범위로 거른다. 양 끝은 포함한다")
    void byPeriod() {
        var page = settlementSummaryQuery.findAll(
            search(null, LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 30)), PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting("periodEndOn")
            .containsExactly(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 8, 31));
    }

    @Test
    @DisplayName("페이지 크기만큼만 담고 다음 페이지 여부를 준다")
    void paging() {
        var page = settlementSummaryQuery.findAll(search(null, null, null), PageRequest.of(0, 2));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.hasNext()).isTrue();
    }

    private SettlementSearch search(SettlementStatus status, LocalDate fromOn, LocalDate toOn) {
        return new SettlementSearch(SettlementFixture.SELLER_ID, status, fromOn, toOn);
    }
}
