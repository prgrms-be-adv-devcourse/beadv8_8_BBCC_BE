package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementProperties;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SettlementPropertiesTest {

    private static final String RUN_CRON = "0 0 3 1 * *";
    private static final String PAYOUT_RETRY_CRON = "0 */10 * * * *";

    @Test
    @DisplayName("올바른 수수료율이면 만든다")
    void create() {
        var properties = new SettlementProperties(new BigDecimal("0.1000"), RUN_CRON, PAYOUT_RETRY_CRON, 100);

        assertThat(properties.feeRate()).isEqualByComparingTo("0.1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.5", "-0.1", "0.12345"})
    @DisplayName("수수료율이 범위를 벗어나거나 소수점 넷째 자리를 넘으면 시작하지 않는다")
    void rejectsInvalidFeeRate(String feeRate) {
        assertThatThrownBy(() -> new SettlementProperties(new BigDecimal(feeRate), RUN_CRON, PAYOUT_RETRY_CRON, 100))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("지급 재요청 cron이 비어 있으면 시작하지 않는다")
    void rejectsBlankPayoutRetryCron() {
        assertThatThrownBy(() -> new SettlementProperties(new BigDecimal("0.1"), RUN_CRON, " ", 100))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
