package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.payment.domain.Charge;
import com.bbcc.kidly.boundedcontext.payment.domain.ChargeCancellationStatus;
import com.bbcc.kidly.boundedcontext.payment.domain.ChargeStatus;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentErrorCode;
import com.bbcc.kidly.global.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChargeTest {

    @ParameterizedTest
    @ValueSource(longs = {1_000L, 1_000_000L})
    @DisplayName("1천~100만 원 충전을 요청하면 대기 상태가 된다")
    void requestCreatesPendingCharge(long amount) {
        Charge charge = PaymentFixture.charge(amount);

        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.PENDING);
        assertThat(charge.getAmount()).isEqualTo(amount);
    }

    @ParameterizedTest
    @ValueSource(longs = {999L, 1_000_001L})
    @DisplayName("1천 원 미만이나 100만 원 초과는 충전할 수 없다")
    void requestRejectsAmountOutOfRange(long amount) {
        assertThatThrownBy(() -> PaymentFixture.charge(amount))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(PaymentErrorCode.CHARGE_AMOUNT_OUT_OF_RANGE);
    }

    @Test
    @DisplayName("요청 금액과 같은 금액으로 승인을 시작하면 승인 중 상태가 된다")
    void startConfirmingWithSameAmount() {
        Charge charge = PaymentFixture.charge(10_000L);

        charge.startConfirming(10_000L, NOW);

        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.CONFIRMING);
    }

    @Test
    @DisplayName("요청 금액과 다른 금액으로는 승인을 시작할 수 없다")
    void startConfirmingRejectsDifferentAmount() {
        Charge charge = PaymentFixture.charge(10_000L);

        assertThatThrownBy(() -> charge.startConfirming(9_000L, NOW))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(PaymentErrorCode.CHARGE_AMOUNT_MISMATCH);
        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.PENDING);
    }

    @Test
    @DisplayName("만료 시각이 지나면 승인을 시작할 수 없다")
    void startConfirmingRejectsExpiredCharge() {
        Charge charge = PaymentFixture.charge(10_000L);

        assertThatThrownBy(() -> charge.startConfirming(10_000L, NOW.plusMinutes(31)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(PaymentErrorCode.CHARGE_EXPIRED);
    }

    @Test
    @DisplayName("같은 충전을 두 번 승인 시작할 수 없다")
    void startConfirmingTwiceFails() {
        Charge charge = PaymentFixture.charge(10_000L);
        charge.startConfirming(10_000L, NOW);

        assertThatThrownBy(() -> charge.startConfirming(10_000L, NOW))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(PaymentErrorCode.INVALID_CHARGE_STATUS);
    }

    @Test
    @DisplayName("승인되면 완료 상태가 되고 결제 키와 승인 시각이 남는다")
    void approveCompletesCharge() {
        Charge charge = PaymentFixture.approvedCharge(10_000L);

        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.DONE);
        assertThat(charge.getPaymentKeyEnc()).isEqualTo("encrypted-payment-key");
        assertThat(charge.getApprovedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("승인 중이 아니면 승인할 수 없다")
    void approveRequiresConfirming() {
        Charge charge = PaymentFixture.charge(10_000L);

        assertThatThrownBy(() -> charge.approve("key", NOW))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(PaymentErrorCode.INVALID_CHARGE_STATUS);
    }

    @Test
    @DisplayName("승인 중에 PG 승인이 실패하면 실패 상태가 되고 PG 오류 코드가 남는다")
    void failMarksFailed() {
        Charge charge = PaymentFixture.charge(10_000L);
        charge.startConfirming(10_000L, NOW);

        charge.fail("REJECT_CARD_COMPANY");

        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.FAILED);
        assertThat(charge.getFailureCode()).isEqualTo("REJECT_CARD_COMPANY");
    }

    @Test
    @DisplayName("대기 중인 충전은 만료할 수 있다")
    void expireMarksExpired() {
        Charge charge = PaymentFixture.charge(10_000L);

        charge.expire();

        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.EXPIRED);
    }

    @Test
    @DisplayName("완료된 충전의 취소를 요청하면 취소 중 상태가 되고 전액 취소 요청이 남는다")
    void requestCancelStartsCancelling() {
        Charge charge = PaymentFixture.approvedCharge(10_000L);

        charge.requestCancel("단순 변심");

        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.CANCELLING);
        assertThat(charge.getCancellations()).singleElement().satisfies(cancellation -> {
            assertThat(cancellation.getAmount()).isEqualTo(10_000L);
            assertThat(cancellation.getReason()).isEqualTo("단순 변심");
            assertThat(cancellation.getStatus()).isEqualTo(ChargeCancellationStatus.REQUESTED);
        });
    }

    @Test
    @DisplayName("마지막 취소 요청은 가장 최근에 만든 취소 요청이다")
    void lastCancellation() {
        Charge charge = PaymentFixture.approvedCharge(10_000L);
        charge.requestCancel("첫 요청");
        charge.failCancel("PROVIDER_ERROR");

        charge.requestCancel("다시 요청");

        assertThat(charge.getLastCancellation().getReason()).isEqualTo("다시 요청");
    }

    @Test
    @DisplayName("완료되지 않은 충전은 취소를 요청할 수 없다")
    void requestCancelRequiresDone() {
        Charge charge = PaymentFixture.charge(10_000L);

        assertThatThrownBy(() -> charge.requestCancel("단순 변심"))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(PaymentErrorCode.INVALID_CHARGE_STATUS);
    }

    @Test
    @DisplayName("PG 취소가 끝나면 충전과 취소 요청이 모두 완료된다")
    void completeCancelMarksCancelled() {
        Charge charge = PaymentFixture.approvedCharge(10_000L);
        charge.requestCancel("단순 변심");

        charge.completeCancel(NOW);

        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.CANCELLED);
        assertThat(charge.getCancellations()).singleElement().satisfies(cancellation -> {
            assertThat(cancellation.getStatus()).isEqualTo(ChargeCancellationStatus.COMPLETED);
            assertThat(cancellation.getCancelledAt()).isEqualTo(NOW);
        });
    }

    @Test
    @DisplayName("PG 취소가 실패하면 PG 오류 코드가 남고 충전은 완료 상태로 돌아가 다시 취소를 요청할 수 있다")
    void failCancelRestoresDone() {
        Charge charge = PaymentFixture.approvedCharge(10_000L);
        charge.requestCancel("단순 변심");

        charge.failCancel("PROVIDER_ERROR");
        charge.requestCancel("다시 요청");

        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.CANCELLING);
        assertThat(charge.getCancellations()).extracting("status", "failureCode").containsExactly(
            tuple(ChargeCancellationStatus.FAILED, "PROVIDER_ERROR"),
            tuple(ChargeCancellationStatus.REQUESTED, null));
    }
}
