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
    @DisplayName("사유는 예외 이름: 메시지이고, 메시지가 없으면 예외 이름만 남긴다")
    void reason() {
        var withMessage = create(new IllegalArgumentException("판매 합계는 0 이상이어야 합니다"));
        var withoutMessage = create(new ArithmeticException());

        assertThat(withMessage.getReason()).isEqualTo("IllegalArgumentException: 판매 합계는 0 이상이어야 합니다");
        assertThat(withMessage.getJobExecutionId()).isEqualTo(7L);
        assertThat(withMessage.getSellerId()).isEqualTo(10L);
        assertThat(withMessage.getPhase()).isEqualTo(SettlementSkipPhase.PROCESS);
        assertThat(withoutMessage.getReason()).isEqualTo("ArithmeticException");
    }

    @Test
    @DisplayName("사유는 200자에서 자른다")
    void reasonTruncated() {
        var skip = create(new IllegalStateException("x".repeat(300)));

        assertThat(skip.getReason()).hasSize(200).startsWith("IllegalStateException: x");
    }

    @Test
    @DisplayName("실행 ID · 판매자 · 단계 · 예외 없이 만들지 않는다")
    void createRejectsInvalid() {
        var error = new IllegalStateException();

        assertThatThrownBy(() -> SettlementSkip.create(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SettlementSkip.create(info(null, 10L, SettlementSkipPhase.WRITE, error)))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SettlementSkip.create(info(0L, 10L, SettlementSkipPhase.WRITE, error)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SettlementSkip.create(info(7L, 0L, SettlementSkipPhase.WRITE, error)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SettlementSkip.create(info(7L, 10L, null, error)))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SettlementSkip.create(info(7L, 10L, SettlementSkipPhase.WRITE, null)))
            .isInstanceOf(NullPointerException.class);
    }

    private static SettlementSkip create(Throwable error) {
        return SettlementSkip.create(info(7L, 10L, SettlementSkipPhase.PROCESS, error));
    }

    private static SettlementSkipCreateInfo info(
        Long jobExecutionId, Long sellerId, SettlementSkipPhase phase, Throwable error) {
        return new SettlementSkipCreateInfo(jobExecutionId, sellerId, phase, error);
    }
}
