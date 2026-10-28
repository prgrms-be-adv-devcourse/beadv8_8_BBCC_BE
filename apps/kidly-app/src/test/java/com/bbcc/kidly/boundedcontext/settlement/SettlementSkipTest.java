package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementSkip;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementSkipCreateInfo;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementSkipPhase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SettlementSkipTest {

    @Test
    @DisplayName("실행 ID · 판매자 · 단계 · 사유를 담는다")
    void create() {
        var skip = SettlementSkip.create(info(7L, 10L, SettlementSkipPhase.PROCESS, "ArithmeticException"));

        assertThat(skip.getJobExecutionId()).isEqualTo(7L);
        assertThat(skip.getSellerId()).isEqualTo(10L);
        assertThat(skip.getPhase()).isEqualTo(SettlementSkipPhase.PROCESS);
        assertThat(skip.getReason()).isEqualTo("ArithmeticException");
    }

    @Test
    @DisplayName("사유는 200자에서 자른다")
    void reasonTruncated() {
        var skip = SettlementSkip.create(info(7L, 10L, SettlementSkipPhase.WRITE, "x".repeat(300)));

        assertThat(skip.getReason()).hasSize(200);
    }

    @Test
    @DisplayName("실행 ID · 판매자 · 단계 · 사유 없이 만들지 않는다")
    void createRejectsInvalid() {
        assertThatThrownBy(() -> SettlementSkip.create(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SettlementSkip.create(info(null, 10L, SettlementSkipPhase.WRITE, "사유")))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SettlementSkip.create(info(0L, 10L, SettlementSkipPhase.WRITE, "사유")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SettlementSkip.create(info(7L, 0L, SettlementSkipPhase.WRITE, "사유")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SettlementSkip.create(info(7L, 10L, null, "사유")))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SettlementSkip.create(info(7L, 10L, SettlementSkipPhase.WRITE, " ")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static SettlementSkipCreateInfo info(
        Long jobExecutionId, Long sellerId, SettlementSkipPhase phase, String reason) {
        return new SettlementSkipCreateInfo(jobExecutionId, sellerId, phase, reason);
    }
}
