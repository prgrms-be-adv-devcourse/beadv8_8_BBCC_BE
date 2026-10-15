package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.payment.domain.Payout;
import com.bbcc.kidly.boundedcontext.payment.domain.PayoutStatus;
import com.bbcc.kidly.shared.payment.domain.PayoutFailureCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PayoutTest {

    private static final Long SETTLEMENT_ID = 31L;
    private static final Long LEDGER_ID = 4001L;

    @Test
    @DisplayName("입금하면 지급 완료로 남고 원장 ID와 지급 시각을 기억한다")
    void complete() {
        Payout payout = Payout.complete(SETTLEMENT_ID, MEMBER_ID, 270_000L, LEDGER_ID, NOW);

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.COMPLETED);
        assertThat(payout.getSettlementId()).isEqualTo(SETTLEMENT_ID);
        assertThat(payout.getSellerId()).isEqualTo(MEMBER_ID);
        assertThat(payout.getAmount()).isEqualTo(270_000L);
        assertThat(payout.getLedgerId()).isEqualTo(LEDGER_ID);
        assertThat(payout.getPaidAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("0원 지급은 원장 없이 지급 완료로 남는다")
    void completeZero() {
        Payout payout = Payout.complete(SETTLEMENT_ID, MEMBER_ID, 0L, null, NOW);

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.COMPLETED);
        assertThat(payout.getLedgerId()).isNull();
    }

    @Test
    @DisplayName("입금하지 못하면 지급 실패로 남고 실패 코드와 실패 시각을 기억한다")
    void fail() {
        Payout payout = Payout.fail(SETTLEMENT_ID, MEMBER_ID, 270_000L, PayoutFailureCode.WALLET_NOT_FOUND, NOW);

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.FAILED);
        assertThat(payout.getFailureCode()).isEqualTo(PayoutFailureCode.WALLET_NOT_FOUND);
        assertThat(payout.getFailedAt()).isEqualTo(NOW);
        assertThat(payout.getPaidAt()).isNull();
    }
}
