package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.CANCELLATION_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.CHARGE_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.TOSS_ORDER_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.WALLET_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.bbcc.kidly.boundedcontext.payment.app.ChargeService;
import com.bbcc.kidly.boundedcontext.payment.app.dto.ChargeCancelResult;
import com.bbcc.kidly.boundedcontext.payment.app.dto.ChargeCancelTarget;
import com.bbcc.kidly.boundedcontext.payment.app.dto.ChargeConfirmResult;
import com.bbcc.kidly.boundedcontext.payment.app.dto.ChargeConfirmTarget;
import com.bbcc.kidly.boundedcontext.payment.app.dto.ChargeDetail;
import com.bbcc.kidly.boundedcontext.payment.app.dto.ChargeRequest;
import com.bbcc.kidly.boundedcontext.payment.app.dto.ChargeRequestResult;
import com.bbcc.kidly.boundedcontext.payment.app.dto.ConfirmChargeRequest;
import com.bbcc.kidly.boundedcontext.payment.app.port.PaymentKeyCipher;
import com.bbcc.kidly.boundedcontext.payment.app.port.PgApproval;
import com.bbcc.kidly.boundedcontext.payment.app.port.PgCancellation;
import com.bbcc.kidly.boundedcontext.payment.domain.Charge;
import com.bbcc.kidly.boundedcontext.payment.domain.ChargeCancellationStatus;
import com.bbcc.kidly.boundedcontext.payment.domain.ChargeRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.ChargeStatus;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentErrorCode;
import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedger;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerType;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChargeServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private ChargeRepository chargeRepository;

    @Mock
    private WalletLedgerRepository walletLedgerRepository;

    @Mock
    private PaymentKeyCipher paymentKeyCipher;

    @InjectMocks
    private ChargeService chargeService;

    @Test
    @DisplayName("충전을 요청하면 UUID 주문번호, 30분 뒤 만료로 대기 중인 충전을 저장한다")
    void request() {
        given(walletRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(PaymentFixture.savedWallet(0L)));
        given(chargeRepository.save(any())).willAnswer(returnsFirstArg());

        ChargeRequestResult result = chargeService.request(MEMBER_ID, new ChargeRequest(10_000L));

        ArgumentCaptor<Charge> charge = ArgumentCaptor.forClass(Charge.class);
        then(chargeRepository).should().save(charge.capture());
        assertThat(charge.getValue().getWalletId()).isEqualTo(WALLET_ID);
        assertThat(charge.getValue().getStatus()).isEqualTo(ChargeStatus.PENDING);
        assertThat(charge.getValue().getTossOrderId()).hasSize(36);
        assertThat(charge.getValue().getExpiresAt())
            .isCloseTo(LocalDateTime.now().plusMinutes(30), within(5, ChronoUnit.SECONDS));
        assertThat(result.orderId()).isEqualTo(charge.getValue().getTossOrderId());
        assertThat(result.amount()).isEqualTo(10_000L);
        assertThat(result.expiresAt()).isEqualTo(charge.getValue().getExpiresAt());
    }

    @Test
    @DisplayName("지갑이 없으면 충전을 요청할 수 없다")
    void requestWithoutWallet() {
        given(walletRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chargeService.request(MEMBER_ID, new ChargeRequest(10_000L)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(PaymentErrorCode.WALLET_NOT_FOUND);
    }

    @Test
    @DisplayName("충전 목록은 회원 지갑의 충전을 최신순 페이지로 준다")
    void getCharges() {
        Charge charge = PaymentFixture.savedCharge(10_000L);
        given(walletRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(PaymentFixture.savedWallet(0L)));
        given(chargeRepository.findAllByWalletIdOrderByIdDesc(WALLET_ID, PageRequest.of(1, 20)))
            .willReturn(new PageImpl<>(List.of(charge), PageRequest.of(1, 20), 21));

        Page<ChargeDetail> charges = chargeService.getCharges(MEMBER_ID, 1, 20);

        assertThat(charges.getContent()).extracting(ChargeDetail::chargeId).containsExactly(CHARGE_ID);
        assertThat(charges.getContent().getFirst().status()).isEqualTo("PENDING");
        assertThat(charges.getTotalElements()).isEqualTo(21);
    }

    @Test
    @DisplayName("승인을 시작하면 금액을 대조하고 승인 중으로 바꾼다")
    void startConfirming() {
        Charge charge = PaymentFixture.savedCharge(10_000L);
        given(walletRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(PaymentFixture.savedWallet(0L)));
        given(chargeRepository.findWithLockByTossOrderId(TOSS_ORDER_ID)).willReturn(Optional.of(charge));

        ChargeConfirmTarget target = chargeService.startConfirming(MEMBER_ID, confirmRequest(10_000L));

        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.CONFIRMING);
        assertThat(target).isEqualTo(new ChargeConfirmTarget(CHARGE_ID, TOSS_ORDER_ID, 10_000L));
    }

    @Test
    @DisplayName("남의 지갑 충전은 없는 충전처럼 404다")
    void othersCharge() {
        Wallet mine = PaymentFixture.savedWallet(0L);
        ReflectionTestUtils.setField(mine, "id", 999L);
        given(walletRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(mine));
        given(chargeRepository.findWithLockByTossOrderId(TOSS_ORDER_ID))
            .willReturn(Optional.of(PaymentFixture.savedCharge(10_000L)));

        assertThatThrownBy(() -> chargeService.startConfirming(MEMBER_ID, confirmRequest(10_000L)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(PaymentErrorCode.CHARGE_NOT_FOUND);
    }

    @Test
    @DisplayName("승인되면 paymentKey를 암호화해 저장하고 지갑에 입금한 뒤 원장에 CHARGE를 남긴다")
    void approve() {
        Charge charge = PaymentFixture.savedCharge(10_000L);
        charge.startConfirming(10_000L, LocalDateTime.now());
        Wallet wallet = PaymentFixture.savedWallet(5_000L);
        LocalDateTime approvedAt = LocalDateTime.now();
        given(chargeRepository.findWithLockById(CHARGE_ID)).willReturn(Optional.of(charge));
        given(walletRepository.findWithLockById(WALLET_ID)).willReturn(Optional.of(wallet));
        given(paymentKeyCipher.encrypt("payment-key")).willReturn("encrypted");

        ChargeConfirmResult result = chargeService.approve(CHARGE_ID,
            new PgApproval("payment-key", 10_000L, approvedAt));

        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.DONE);
        assertThat(charge.getPaymentKeyEnc()).isEqualTo("encrypted");
        assertThat(charge.getApprovedAt()).isEqualTo(approvedAt);
        assertThat(wallet.getBalance()).isEqualTo(15_000L);
        ArgumentCaptor<WalletLedger> ledger = ArgumentCaptor.forClass(WalletLedger.class);
        then(walletLedgerRepository).should().save(ledger.capture());
        assertThat(ledger.getValue().getType()).isEqualTo(WalletLedgerType.CHARGE);
        assertThat(ledger.getValue().getAmount()).isEqualTo(10_000L);
        assertThat(ledger.getValue().getBalanceAfter()).isEqualTo(15_000L);
        assertThat(ledger.getValue().getRefId()).isEqualTo(CHARGE_ID);
        assertThat(result).isEqualTo(new ChargeConfirmResult(CHARGE_ID, "DONE", 10_000L, 15_000L));
    }

    @Test
    @DisplayName("거절되면 토스 오류 코드와 함께 실패로 바꾼다")
    void fail() {
        Charge charge = PaymentFixture.savedCharge(10_000L);
        charge.startConfirming(10_000L, LocalDateTime.now());
        given(chargeRepository.findWithLockById(CHARGE_ID)).willReturn(Optional.of(charge));

        chargeService.fail(CHARGE_ID, "REJECT_CARD_COMPANY");

        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.FAILED);
        assertThat(charge.getFailureCode()).isEqualTo("REJECT_CARD_COMPANY");
    }

    @Test
    @DisplayName("취소를 시작하면 취소 중으로 바꾸고 충전액을 먼저 빼서 원장에 CHARGE_CANCEL(ref_id = 취소 요청 ID)을 남긴다")
    void startCancel() {
        Charge charge = PaymentFixture.savedApprovedCharge(10_000L);
        Wallet wallet = PaymentFixture.savedWallet(15_000L);
        given(chargeRepository.findWithLockById(CHARGE_ID)).willReturn(Optional.of(charge));
        given(walletRepository.findWithLockById(WALLET_ID)).willReturn(Optional.of(wallet));
        given(chargeRepository.saveAndFlush(charge)).willAnswer(invocation -> {
            ReflectionTestUtils.setField(charge.getLastCancellation(), "id", CANCELLATION_ID);
            return charge;
        });

        ChargeCancelTarget target = chargeService.startCancel(MEMBER_ID, CHARGE_ID);

        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.CANCELLING);
        assertThat(charge.getLastCancellation().getAmount()).isEqualTo(10_000L);
        assertThat(wallet.getBalance()).isEqualTo(5_000L);
        ArgumentCaptor<WalletLedger> ledger = ArgumentCaptor.forClass(WalletLedger.class);
        then(walletLedgerRepository).should().save(ledger.capture());
        assertThat(ledger.getValue().getType()).isEqualTo(WalletLedgerType.CHARGE_CANCEL);
        assertThat(ledger.getValue().getAmount()).isEqualTo(-10_000L);
        assertThat(ledger.getValue().getBalanceAfter()).isEqualTo(5_000L);
        assertThat(ledger.getValue().getRefId()).isEqualTo(CANCELLATION_ID);
        assertThat(target).isEqualTo(
            new ChargeCancelTarget(CHARGE_ID, CANCELLATION_ID, "encrypted-payment-key", "예치금 충전 취소", 10_000L));
    }

    @Test
    @DisplayName("잔액이 충전액보다 적으면 취소할 수 없다")
    void startCancelBalanceShort() {
        Charge charge = PaymentFixture.savedApprovedCharge(10_000L);
        given(chargeRepository.findWithLockById(CHARGE_ID)).willReturn(Optional.of(charge));
        given(walletRepository.findWithLockById(WALLET_ID))
            .willReturn(Optional.of(PaymentFixture.savedWallet(9_999L)));

        assertThatThrownBy(() -> chargeService.startCancel(MEMBER_ID, CHARGE_ID))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(PaymentErrorCode.CHARGE_CANCEL_BALANCE_SHORT);
        then(walletLedgerRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("남의 충전은 없는 충전처럼 취소할 수 없다")
    void startCancelOthersCharge() {
        given(chargeRepository.findWithLockById(CHARGE_ID))
            .willReturn(Optional.of(PaymentFixture.savedApprovedCharge(10_000L)));
        given(walletRepository.findWithLockById(WALLET_ID))
            .willReturn(Optional.of(PaymentFixture.savedWallet(10_000L)));

        assertThatThrownBy(() -> chargeService.startCancel(2L, CHARGE_ID))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(PaymentErrorCode.CHARGE_NOT_FOUND);
    }

    @Test
    @DisplayName("토스 취소가 끝나면 충전과 취소 요청을 완료로 바꾸고 잔액은 그대로다")
    void completeCancel() {
        Charge charge = PaymentFixture.savedCancellingCharge(10_000L);
        Wallet wallet = PaymentFixture.savedWallet(5_000L);
        LocalDateTime cancelledAt = LocalDateTime.now();
        given(chargeRepository.findWithLockById(CHARGE_ID)).willReturn(Optional.of(charge));
        given(walletRepository.findWithLockById(WALLET_ID)).willReturn(Optional.of(wallet));

        ChargeCancelResult result = chargeService.completeCancel(CHARGE_ID, new PgCancellation(cancelledAt));

        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.CANCELLED);
        assertThat(charge.getLastCancellation().getCancelledAt()).isEqualTo(cancelledAt);
        assertThat(wallet.getBalance()).isEqualTo(5_000L);
        then(walletLedgerRepository).should(never()).save(any());
        assertThat(result).isEqualTo(new ChargeCancelResult(CHARGE_ID, "CANCELLED", 10_000L, 5_000L));
    }

    @Test
    @DisplayName("토스 취소 시각이 없으면 지금 시각으로 완료한다")
    void completeCancelWithoutCancelledAt() {
        Charge charge = PaymentFixture.savedCancellingCharge(10_000L);
        given(chargeRepository.findWithLockById(CHARGE_ID)).willReturn(Optional.of(charge));
        given(walletRepository.findWithLockById(WALLET_ID)).willReturn(Optional.of(PaymentFixture.savedWallet(0L)));

        chargeService.completeCancel(CHARGE_ID, new PgCancellation(null));

        assertThat(charge.getLastCancellation().getCancelledAt())
            .isCloseTo(LocalDateTime.now(), within(5, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("토스가 취소를 거절하면 충전을 완료로 되돌리고 뺀 돈을 다시 넣어 원장에 CHARGE_CANCEL_REVERT를 남긴다")
    void failCancel() {
        Charge charge = PaymentFixture.savedCancellingCharge(10_000L);
        Wallet wallet = PaymentFixture.savedWallet(5_000L);
        given(chargeRepository.findWithLockById(CHARGE_ID)).willReturn(Optional.of(charge));
        given(walletRepository.findWithLockById(WALLET_ID)).willReturn(Optional.of(wallet));

        chargeService.failCancel(CHARGE_ID, "NOT_CANCELABLE_PAYMENT");

        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.DONE);
        assertThat(charge.getLastCancellation().getStatus()).isEqualTo(ChargeCancellationStatus.FAILED);
        assertThat(charge.getLastCancellation().getFailureCode()).isEqualTo("NOT_CANCELABLE_PAYMENT");
        assertThat(wallet.getBalance()).isEqualTo(15_000L);
        ArgumentCaptor<WalletLedger> ledger = ArgumentCaptor.forClass(WalletLedger.class);
        then(walletLedgerRepository).should().save(ledger.capture());
        assertThat(ledger.getValue().getType()).isEqualTo(WalletLedgerType.CHARGE_CANCEL_REVERT);
        assertThat(ledger.getValue().getAmount()).isEqualTo(10_000L);
        assertThat(ledger.getValue().getBalanceAfter()).isEqualTo(15_000L);
        assertThat(ledger.getValue().getRefId()).isEqualTo(CANCELLATION_ID);
    }

    @Test
    @DisplayName("토스에서 직접 취소된 충전은 취소 완료로 바꾸고 충전액을 빼서 원장에 CHARGE_CANCEL(ref_id = 취소 요청 ID)을 남긴다")
    void applyExternalCancel() {
        Charge charge = PaymentFixture.savedApprovedCharge(10_000L);
        Wallet wallet = PaymentFixture.savedWallet(15_000L);
        LocalDateTime cancelledAt = LocalDateTime.now();
        given(chargeRepository.findWithLockById(CHARGE_ID)).willReturn(Optional.of(charge));
        given(walletRepository.findWithLockById(WALLET_ID)).willReturn(Optional.of(wallet));
        given(chargeRepository.saveAndFlush(charge)).willAnswer(invocation -> {
            ReflectionTestUtils.setField(charge.getLastCancellation(), "id", CANCELLATION_ID);
            return charge;
        });

        chargeService.applyExternalCancel(CHARGE_ID, new PgCancellation(cancelledAt));

        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.CANCELLED);
        assertThat(charge.getLastCancellation().getStatus()).isEqualTo(ChargeCancellationStatus.COMPLETED);
        assertThat(charge.getLastCancellation().getCancelledAt()).isEqualTo(cancelledAt);
        assertThat(wallet.getBalance()).isEqualTo(5_000L);
        ArgumentCaptor<WalletLedger> ledger = ArgumentCaptor.forClass(WalletLedger.class);
        then(walletLedgerRepository).should().save(ledger.capture());
        assertThat(ledger.getValue().getType()).isEqualTo(WalletLedgerType.CHARGE_CANCEL);
        assertThat(ledger.getValue().getAmount()).isEqualTo(-10_000L);
        assertThat(ledger.getValue().getBalanceAfter()).isEqualTo(5_000L);
        assertThat(ledger.getValue().getRefId()).isEqualTo(CANCELLATION_ID);
    }

    @Test
    @DisplayName("잔액이 충전액보다 적으면 있는 만큼만 빼고 원장에도 뺀 만큼만 남긴다")
    void applyExternalCancelBalanceShort() {
        Charge charge = PaymentFixture.savedApprovedCharge(10_000L);
        Wallet wallet = PaymentFixture.savedWallet(3_000L);
        given(chargeRepository.findWithLockById(CHARGE_ID)).willReturn(Optional.of(charge));
        given(walletRepository.findWithLockById(WALLET_ID)).willReturn(Optional.of(wallet));
        given(chargeRepository.saveAndFlush(charge)).willReturn(charge);

        chargeService.applyExternalCancel(CHARGE_ID, new PgCancellation(LocalDateTime.now()));

        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.CANCELLED);
        assertThat(charge.getLastCancellation().getAmount()).isEqualTo(10_000L);
        assertThat(wallet.getBalance()).isZero();
        ArgumentCaptor<WalletLedger> ledger = ArgumentCaptor.forClass(WalletLedger.class);
        then(walletLedgerRepository).should().save(ledger.capture());
        assertThat(ledger.getValue().getAmount()).isEqualTo(-3_000L);
        assertThat(ledger.getValue().getBalanceAfter()).isZero();
    }

    @Test
    @DisplayName("잔액이 0이면 원장을 남기지 않고 충전만 취소 완료로 바꾼다")
    void applyExternalCancelEmptyWallet() {
        Charge charge = PaymentFixture.savedApprovedCharge(10_000L);
        given(chargeRepository.findWithLockById(CHARGE_ID)).willReturn(Optional.of(charge));
        given(walletRepository.findWithLockById(WALLET_ID)).willReturn(Optional.of(PaymentFixture.savedWallet(0L)));
        given(chargeRepository.saveAndFlush(charge)).willReturn(charge);

        chargeService.applyExternalCancel(CHARGE_ID, new PgCancellation(LocalDateTime.now()));

        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.CANCELLED);
        then(walletLedgerRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("그사이 완료가 아니게 된 충전(이미 취소됨 · 우리 취소 진행 중)은 그대로 둔다")
    void applyExternalCancelNotDone() {
        Charge charge = PaymentFixture.savedCancellingCharge(10_000L);
        given(chargeRepository.findWithLockById(CHARGE_ID)).willReturn(Optional.of(charge));

        chargeService.applyExternalCancel(CHARGE_ID, new PgCancellation(LocalDateTime.now()));

        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.CANCELLING);
        then(walletRepository).should(never()).findWithLockById(any());
        then(walletLedgerRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("토스 취소 시각이 없으면 지금 시각으로 직접 취소를 반영한다")
    void applyExternalCancelWithoutCancelledAt() {
        Charge charge = PaymentFixture.savedApprovedCharge(10_000L);
        given(chargeRepository.findWithLockById(CHARGE_ID)).willReturn(Optional.of(charge));
        given(walletRepository.findWithLockById(WALLET_ID)).willReturn(Optional.of(PaymentFixture.savedWallet(0L)));
        given(chargeRepository.saveAndFlush(charge)).willReturn(charge);

        chargeService.applyExternalCancel(CHARGE_ID, new PgCancellation(null));

        assertThat(charge.getLastCancellation().getCancelledAt())
            .isCloseTo(LocalDateTime.now(), within(5, ChronoUnit.SECONDS));
    }

    private static ConfirmChargeRequest confirmRequest(long amount) {
        return new ConfirmChargeRequest("payment-key", TOSS_ORDER_ID, amount);
    }
}
