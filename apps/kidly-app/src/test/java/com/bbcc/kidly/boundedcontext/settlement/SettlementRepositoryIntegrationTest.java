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
    @DisplayName("같은 판매자 · 같은 기간의 정산서는 두 번 저장되지 않는다")
    void uniqueSellerPeriod() {
        settlementRepository.save(SettlementFixture.settlement(1_000L));

        assertThatThrownBy(() -> settlementRepository.save(SettlementFixture.settlement(2_000L)))
            .isInstanceOf(DataIntegrityViolationException.class);
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
}
