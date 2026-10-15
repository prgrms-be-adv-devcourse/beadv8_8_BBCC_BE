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
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SettlementTest {

    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 11, 1, 0, 5);

    private static SettlementCreateInfo info(long total, String feeRate) {
        return new SettlementCreateInfo(SELLER_ID, PERIOD_START, PERIOD_END, total, new BigDecimal(feeRate));
    }

    @Test
    @DisplayName("수수료는 판매 합계 × 수수료율에서 원 미만을 버리고, 지급 요청 상태로 만든다")
    void create() {
        var settlement = Settlement.create(info(10_009L, "0.1"), NOW);

        assertThat(settlement.getTotalSalesAmount()).isEqualTo(10_009L);
        assertThat(settlement.getFeeRate()).isEqualByComparingTo(FEE_RATE);
        assertThat(settlement.getFeeAmount()).isEqualTo(1_000L);
        assertThat(settlement.getSettlementAmount()).isEqualTo(9_009L);
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(settlement.getCompletedAt()).isEqualTo(NOW);
        assertThat(settlement.getPaidAt()).isNull();
        assertThat(settlement.getFailReason()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"0, 0.1, 0, 0", "10000, 0, 0, 10000", "10000, 1, 10000, 0", "9, 0.1, 0, 9"})
    @DisplayName("0원 · 수수료율 경계에서도 수수료 + 정산금액 = 판매 합계")
    void createBoundary(long total, String feeRate, long fee, long amount) {
        var settlement = Settlement.create(info(total, feeRate), NOW);

        assertThat(settlement.getFeeAmount()).isEqualTo(fee);
        assertThat(settlement.getSettlementAmount()).isEqualTo(amount);
    }

    @Test
    @DisplayName("수동 실행처럼 월 중간에 끝나는 기간도 만든다")
    void createMidMonthPeriod() {
        var settlement = Settlement.create(
            new SettlementCreateInfo(SELLER_ID, PERIOD_START, PERIOD_START.plusDays(14), 1_000L, FEE_RATE), NOW);

        assertThat(settlement.getPeriodEndOn()).isEqualTo(PERIOD_START.plusDays(14));
    }

    @Test
    @DisplayName("필수 값이 없으면 만들지 않는다")
    void createRejectsNull() {
        assertThatThrownBy(() -> Settlement.create(null, NOW)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Settlement.create(SettlementFixture.settlementInfo(1L), null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Settlement.create(
            new SettlementCreateInfo(null, PERIOD_START, PERIOD_END, 1L, FEE_RATE), NOW))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Settlement.create(
            new SettlementCreateInfo(SELLER_ID, null, PERIOD_END, 1L, FEE_RATE), NOW))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Settlement.create(
            new SettlementCreateInfo(SELLER_ID, PERIOD_START, null, 1L, FEE_RATE), NOW))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Settlement.create(
            new SettlementCreateInfo(SELLER_ID, PERIOD_START, PERIOD_END, null, FEE_RATE), NOW))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Settlement.create(
            new SettlementCreateInfo(SELLER_ID, PERIOD_START, PERIOD_END, 1L, null), NOW))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("판매자 ID · 기간 · 합계 · 수수료율이 잘못되면 만들지 않는다")
    void createRejectsInvalidValue() {
        assertThatThrownBy(() -> Settlement.create(
            new SettlementCreateInfo(0L, PERIOD_START, PERIOD_END, 1L, FEE_RATE), NOW))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Settlement.create(
            new SettlementCreateInfo(SELLER_ID, PERIOD_END, PERIOD_START, 1L, FEE_RATE), NOW))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Settlement.create(info(-1L, "0.1"), NOW))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Settlement.create(info(1L, "-0.01"), NOW))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Settlement.create(info(1L, "1.01"), NOW))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Settlement.create(info(1L, "0.12345"), NOW))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("지급 완료를 반영하고, 같은 결과가 다시 와도 처음 지급 시각을 유지한다")
    void markPaid() {
        var settlement = SettlementFixture.settlement(10_000L);

        settlement.markPaid(PAID_AT);
        settlement.markPaid(PAID_AT.plusMinutes(1));

        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.PAID);
        assertThat(settlement.getPaidAt()).isEqualTo(PAID_AT);
    }

    @Test
    @DisplayName("지급 실패를 반영하고, 같은 결과가 다시 와도 처음 사유를 유지한다")
    void markPaymentFailed() {
        var settlement = SettlementFixture.settlement(10_000L);

        settlement.markPaymentFailed("WALLET_NOT_FOUND");
        settlement.markPaymentFailed("OTHER");

        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.FAILED);
        assertThat(settlement.getFailReason()).isEqualTo("WALLET_NOT_FOUND");
    }

    @Test
    @DisplayName("지급 완료와 실패가 엇갈려 오면 상태를 바꾸지 않는다")
    void conflictingResult() {
        var paid = SettlementFixture.settlement(10_000L);
        paid.markPaid(PAID_AT);
        var failed = SettlementFixture.settlement(10_000L);
        failed.markPaymentFailed("WALLET_NOT_FOUND");

        assertThatThrownBy(() -> paid.markPaymentFailed("WALLET_NOT_FOUND"))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> failed.markPaid(PAID_AT)).isInstanceOf(IllegalStateException.class);
        assertThat(paid.getStatus()).isEqualTo(SettlementStatus.PAID);
        assertThat(failed.getStatus()).isEqualTo(SettlementStatus.FAILED);
    }

    @Test
    @DisplayName("지급 시각 · 실패 사유 없이 결과를 반영하지 않는다")
    void resultRejectsMissingValue() {
        var settlement = SettlementFixture.settlement(10_000L);

        assertThatThrownBy(() -> settlement.markPaid(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> settlement.markPaymentFailed(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
    }
}
