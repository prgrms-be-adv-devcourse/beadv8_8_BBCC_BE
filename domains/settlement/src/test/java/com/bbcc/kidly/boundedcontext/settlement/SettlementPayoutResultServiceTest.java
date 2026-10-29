package com.bbcc.kidly.boundedcontext.settlement;

import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementPayoutResultService;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementStatus;
import com.bbcc.kidly.shared.payment.domain.PayoutFailureCode;
import com.bbcc.kidly.shared.payment.event.PaymentPayoutCompletedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentPayoutFailedEvent;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementPayoutResultServiceTest {

    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 11, 1, 9, 0);
    // 판매 10,000원 · 수수료 10% → 정산금액 9,000원
    private static final long AMOUNT = 9_000L;

    @Mock
    private SettlementRepository settlementRepository;

    @InjectMocks
    private SettlementPayoutResultService service;

    @Test
    @DisplayName("지급 완료 결과가 정산서와 같으면 지급 완료로 바꾸고 지급 시각을 남긴다")
    void markPaid() {
        var settlement = SettlementFixture.settlement(10_000L);
        given(settlementRepository.findById(1L)).willReturn(Optional.of(settlement));

        service.markPaid(completed(1L, SELLER_ID, AMOUNT));

        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.PAID);
        assertThat(settlement.getPaidAt()).isEqualTo(PAID_AT);
    }

    @Test
    @DisplayName("정산서가 없으면 예외를 던진다")
    void settlementMissing() {
        given(settlementRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.markPaid(completed(1L, SELLER_ID, AMOUNT)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("settlementId=");
        assertThatThrownBy(() -> service.markPaymentFailed(failed(1L, SELLER_ID, AMOUNT)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("settlementId=");
    }

    @Test
    @DisplayName("금액이나 판매자가 다르면 예외를 던지고 상태를 그대로 둔다")
    void mismatch() {
        var settlement = SettlementFixture.settlement(10_000L);
        given(settlementRepository.findById(1L)).willReturn(Optional.of(settlement));

        assertThatThrownBy(() -> service.markPaid(completed(1L, SELLER_ID, AMOUNT + 1)))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.markPaid(completed(1L, SELLER_ID + 1, AMOUNT)))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.markPaymentFailed(failed(1L, SELLER_ID, AMOUNT + 1)))
            .isInstanceOf(IllegalStateException.class);

        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(settlement.getPaidAt()).isNull();
        assertThat(settlement.getFailReason()).isNull();
    }

    @Test
    @DisplayName("지급 실패 결과가 정산서와 같으면 지급 실패로 바꾸고 사유 코드를 남긴다")
    void markPaymentFailed() {
        var settlement = SettlementFixture.settlement(10_000L);
        given(settlementRepository.findById(1L)).willReturn(Optional.of(settlement));

        service.markPaymentFailed(failed(1L, SELLER_ID, AMOUNT));

        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.FAILED);
        assertThat(settlement.getFailReason()).isEqualTo("WALLET_NOT_FOUND");
    }

    private static PaymentPayoutCompletedEvent.Data completed(Long settlementId, Long sellerId, long amount) {
        return new PaymentPayoutCompletedEvent.Data(settlementId, sellerId, amount, 7L, PAID_AT);
    }

    private static PaymentPayoutFailedEvent.Data failed(Long settlementId, Long sellerId, long amount) {
        return new PaymentPayoutFailedEvent.Data(
            settlementId, sellerId, amount, PayoutFailureCode.WALLET_NOT_FOUND, PAID_AT);
    }
}
