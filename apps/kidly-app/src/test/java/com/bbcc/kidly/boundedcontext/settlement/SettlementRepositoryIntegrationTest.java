package com.bbcc.kidly.boundedcontext.settlement;

import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.FEE_RATE;
import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.NOW;
import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.PERIOD_END;
import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.PERIOD_START;
import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.settlement.domain.Settlement;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementCreateInfo;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementStatus;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;

@IntegrationTest
class SettlementRepositoryIntegrationTest {

    @Autowired
    private SettlementRepository settlementRepository;

    @Test
    @DisplayName("저장한 정산서의 금액 · 수수료율 · 상태를 다시 읽는다")
    void saveAndFind() {
        var saved = settlementRepository.save(SettlementFixture.settlement(10_009L));

        assertThat(settlementRepository.findById(saved.getId())).hasValueSatisfying(found -> {
            assertThat(found.getFeeRate()).isEqualByComparingTo(FEE_RATE);
            assertThat(found.getFeeAmount()).isEqualTo(1_000L);
            assertThat(found.getSettlementAmount()).isEqualTo(9_009L);
            assertThat(found.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
            assertThat(found.getCompletedAt()).isEqualTo(NOW);
        });
    }

    @Test
    @DisplayName("같은 판매자 · 같은 종료일의 정산서는 시작일이 달라도 두 번 저장되지 않는다")
    void uniqueSellerPeriodEnd() {
        settlementRepository.save(SettlementFixture.settlement(SELLER_ID, PERIOD_START, PERIOD_END));

        assertThatThrownBy(() -> settlementRepository.save(
            SettlementFixture.settlement(SELLER_ID, PERIOD_END.minusDays(2), PERIOD_END)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("종료일이 같아도 판매자가 다르면 따로 저장한다")
    void differentSeller() {
        settlementRepository.save(SettlementFixture.settlement(SELLER_ID, PERIOD_START, PERIOD_END));

        var saved = settlementRepository.save(SettlementFixture.settlement(SELLER_ID + 1, PERIOD_START, PERIOD_END));

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    @DisplayName("종료일이 같은 정산서 중 시작일이 가장 이른 것을 찾고, 없으면 비어 있다")
    void findEarliestStartByEndOn() {
        settlementRepository.save(SettlementFixture.settlement(1L, PERIOD_END.minusDays(1), PERIOD_END));
        settlementRepository.save(SettlementFixture.settlement(2L, PERIOD_END.minusDays(3), PERIOD_END));
        settlementRepository.save(SettlementFixture.settlement(3L, PERIOD_START, PERIOD_END.minusDays(1)));

        assertThat(settlementRepository.findFirstByPeriodEndOnOrderByPeriodStartOnAsc(PERIOD_END))
            .hasValueSatisfying(found -> assertThat(found.getPeriodStartOn()).isEqualTo(PERIOD_END.minusDays(3)));
        assertThat(settlementRepository.findFirstByPeriodEndOnOrderByPeriodStartOnAsc(PERIOD_END.plusDays(1)))
            .isEmpty();
    }

    @Test
    @DisplayName("시작일이 같아도 종료일이 다르면 따로 저장한다")
    void differentPeriodEnd() {
        settlementRepository.save(Settlement.create(
            new SettlementCreateInfo(SELLER_ID, PERIOD_START, PERIOD_START.plusDays(14), 1_000L, FEE_RATE), NOW));

        var saved = settlementRepository.save(Settlement.create(
            new SettlementCreateInfo(SELLER_ID, PERIOD_START, PERIOD_END, 2_000L, FEE_RATE), NOW));

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    @DisplayName("지급 결과를 저장한다")
    void savePaid() {
        var settlement = settlementRepository.save(SettlementFixture.settlement(1_000L));
        settlement.markPaid(NOW.plusMinutes(5));
        settlementRepository.save(settlement);

        assertThat(settlementRepository.findById(settlement.getId())).hasValueSatisfying(found -> {
            assertThat(found.getStatus()).isEqualTo(SettlementStatus.PAID);
            assertThat(found.getPaidAt()).isEqualTo(NOW.plusMinutes(5));
        });
    }

    @Test
    @DisplayName("기간 안의 COMPLETED만 기준 id 다음부터 id 순으로 limit만큼 찾는다")
    void findStuck() {
        var first = saveCompleted(1L);
        var second = saveCompleted(2L);
        saveCompleted(3L);
        var paid = saveCompleted(4L);
        paid.markPaid(NOW.plusMinutes(5));
        settlementRepository.save(paid);

        var found = settlementRepository.findByStatusAndCompletedAtBetweenAndIdGreaterThanOrderByIdAsc(
            SettlementStatus.COMPLETED, NOW.minusMinutes(1), NOW.plusMinutes(1), first.getId(), Limit.of(1));

        assertThat(found).extracting(Settlement::getId).containsExactly(second.getId());
    }

    @Test
    @DisplayName("기간 양끝과 같은 완료 시각도 포함한다")
    void findStuckBoundaries() {
        var saved = saveCompleted(1L);

        var found = settlementRepository.findByStatusAndCompletedAtBetweenAndIdGreaterThanOrderByIdAsc(
            SettlementStatus.COMPLETED, NOW, NOW, 0L, Limit.of(10));

        assertThat(found).extracting(Settlement::getId).containsExactly(saved.getId());
    }

    @Test
    @DisplayName("기준 시각 이전에 완료된 COMPLETED 건수를 센다")
    void countBefore() {
        saveCompleted(1L);
        saveCompleted(2L);
        var paid = saveCompleted(3L);
        paid.markPaid(NOW.plusMinutes(5));
        settlementRepository.save(paid);

        assertThat(settlementRepository.countByStatusAndCompletedAtBefore(
            SettlementStatus.COMPLETED, NOW.plusSeconds(1))).isEqualTo(2);
        assertThat(settlementRepository.countByStatusAndCompletedAtBefore(
            SettlementStatus.COMPLETED, NOW)).isZero();
    }

    private Settlement saveCompleted(Long sellerId) {
        return settlementRepository.save(SettlementFixture.settlement(sellerId, PERIOD_START, PERIOD_END));
    }
}
