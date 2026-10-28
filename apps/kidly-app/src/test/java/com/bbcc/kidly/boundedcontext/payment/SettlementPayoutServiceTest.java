package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.NOW;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.WALLET_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

import com.bbcc.kidly.boundedcontext.payment.app.SettlementPayoutService;
import com.bbcc.kidly.boundedcontext.payment.app.dto.PayoutCommand;
import com.bbcc.kidly.boundedcontext.payment.domain.Payout;
import com.bbcc.kidly.boundedcontext.payment.domain.PayoutRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.PayoutStatus;
import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedger;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerType;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.payment.domain.PayoutFailureCode;
import com.bbcc.kidly.shared.payment.event.PaymentPayoutCompletedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentPayoutFailedEvent;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SettlementPayoutServiceTest {

    private static final Long SETTLEMENT_ID = 31L;
    private static final Long LEDGER_ID = 4001L;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletLedgerRepository walletLedgerRepository;

    @Mock
    private PayoutRepository payoutRepository;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private SettlementPayoutService settlementPayoutService;

    @Test
    @DisplayName("판매자 지갑에 정산금액을 입금하고 원장 SETTLEMENT(ref_id = 정산서 ID)와 지급 완료 기록을 남긴 뒤 payment.payout-completed를 발행한다")
    void payout() {
        Wallet wallet = PaymentFixture.savedWallet(5_000L);
        given(walletRepository.findWithLockByMemberId(MEMBER_ID)).willReturn(Optional.of(wallet));
        given(payoutRepository.findBySettlementId(SETTLEMENT_ID)).willReturn(Optional.empty());
        givenPayoutSaved();
        givenLedgerSaved();

        settlementPayoutService.payout(new PayoutCommand(SETTLEMENT_ID, MEMBER_ID, 270_000L));

        assertThat(wallet.getBalance()).isEqualTo(275_000L);
        ArgumentCaptor<WalletLedger> ledger = ArgumentCaptor.forClass(WalletLedger.class);
        then(walletLedgerRepository).should().save(ledger.capture());
        assertThat(ledger.getValue().getWalletId()).isEqualTo(WALLET_ID);
        assertThat(ledger.getValue().getType()).isEqualTo(WalletLedgerType.SETTLEMENT);
        assertThat(ledger.getValue().getAmount()).isEqualTo(270_000L);
        assertThat(ledger.getValue().getBalanceAfter()).isEqualTo(275_000L);
        assertThat(ledger.getValue().getRefId()).isEqualTo(SETTLEMENT_ID);
        ArgumentCaptor<Payout> payout = ArgumentCaptor.forClass(Payout.class);
        then(payoutRepository).should().save(payout.capture());
        assertThat(payout.getValue().getStatus()).isEqualTo(PayoutStatus.COMPLETED);
        assertThat(payout.getValue().getSettlementId()).isEqualTo(SETTLEMENT_ID);
        assertThat(payout.getValue().getLedgerId()).isEqualTo(LEDGER_ID);
        PaymentPayoutCompletedEvent event = publishedCompleted();
        assertThat(event.data().settlementId()).isEqualTo(SETTLEMENT_ID);
        assertThat(event.data().sellerId()).isEqualTo(MEMBER_ID);
        assertThat(event.data().amount()).isEqualTo(270_000L);
        assertThat(event.data().ledgerId()).isEqualTo(LEDGER_ID);
        assertThat(event.data().paidAt()).isEqualTo(payout.getValue().getPaidAt());
    }

    @Test
    @DisplayName("0원 정산이면 입금 · 원장 없이 지급 완료로 기록하고 ledgerId가 null인 payment.payout-completed를 발행한다")
    void payoutZero() {
        Wallet wallet = PaymentFixture.savedWallet(5_000L);
        given(walletRepository.findWithLockByMemberId(MEMBER_ID)).willReturn(Optional.of(wallet));
        given(payoutRepository.findBySettlementId(SETTLEMENT_ID)).willReturn(Optional.empty());
        givenPayoutSaved();

        settlementPayoutService.payout(new PayoutCommand(SETTLEMENT_ID, MEMBER_ID, 0L));

        assertThat(wallet.getBalance()).isEqualTo(5_000L);
        then(walletLedgerRepository).should(never()).save(any());
        then(payoutRepository).should().save(any(Payout.class));
        PaymentPayoutCompletedEvent event = publishedCompleted();
        assertThat(event.data().amount()).isZero();
        assertThat(event.data().ledgerId()).isNull();
    }

    @Test
    @DisplayName("이미 지급 완료한 정산서면 입금하지 않고 처음 결과로 payment.payout-completed를 다시 발행한다")
    void alreadyCompleted() {
        Wallet wallet = PaymentFixture.savedWallet(5_000L);
        given(walletRepository.findWithLockByMemberId(MEMBER_ID)).willReturn(Optional.of(wallet));
        given(payoutRepository.findBySettlementId(SETTLEMENT_ID)).willReturn(
            Optional.of(Payout.complete(SETTLEMENT_ID, MEMBER_ID, 270_000L, LEDGER_ID, NOW)));

        settlementPayoutService.payout(new PayoutCommand(SETTLEMENT_ID, MEMBER_ID, 270_000L));

        assertThat(wallet.getBalance()).isEqualTo(5_000L);
        then(walletLedgerRepository).should(never()).save(any());
        then(payoutRepository).should(never()).save(any());
        PaymentPayoutCompletedEvent event = publishedCompleted();
        assertThat(event.data().settlementId()).isEqualTo(SETTLEMENT_ID);
        assertThat(event.data().amount()).isEqualTo(270_000L);
        assertThat(event.data().ledgerId()).isEqualTo(LEDGER_ID);
        assertThat(event.data().paidAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("지급에 실패했던 정산서면 지갑이 생겼어도 다시 시도하지 않고 처음 결과로 payment.payout-failed를 다시 발행한다")
    void alreadyFailed() {
        Wallet wallet = PaymentFixture.savedWallet(5_000L);
        given(walletRepository.findWithLockByMemberId(MEMBER_ID)).willReturn(Optional.of(wallet));
        given(payoutRepository.findBySettlementId(SETTLEMENT_ID)).willReturn(Optional.of(
            Payout.fail(SETTLEMENT_ID, MEMBER_ID, 270_000L, PayoutFailureCode.WALLET_NOT_FOUND, NOW)));

        settlementPayoutService.payout(new PayoutCommand(SETTLEMENT_ID, MEMBER_ID, 270_000L));

        assertThat(wallet.getBalance()).isEqualTo(5_000L);
        then(walletLedgerRepository).should(never()).save(any());
        then(payoutRepository).should(never()).save(any());
        PaymentPayoutFailedEvent event = publishedFailed();
        assertThat(event.data().reasonCode()).isEqualTo(PayoutFailureCode.WALLET_NOT_FOUND);
        assertThat(event.data().failedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("판매자 지갑이 없으면 잔액 0인 지갑을 만든 뒤 락으로 읽어 입금한다")
    void createWalletBeforePayout() {
        Wallet wallet = PaymentFixture.savedWallet(0L);
        given(walletRepository.findWithLockByMemberId(MEMBER_ID)).willReturn(Optional.of(wallet));
        given(payoutRepository.findBySettlementId(SETTLEMENT_ID)).willReturn(Optional.empty());
        givenPayoutSaved();
        givenLedgerSaved();

        settlementPayoutService.payout(new PayoutCommand(SETTLEMENT_ID, MEMBER_ID, 270_000L));

        InOrder inOrder = inOrder(walletRepository);
        inOrder.verify(walletRepository).createIfAbsent(eq(MEMBER_ID), any(LocalDateTime.class));
        inOrder.verify(walletRepository).findWithLockByMemberId(MEMBER_ID);
        assertThat(wallet.getBalance()).isEqualTo(270_000L);
        assertThat(publishedCompleted().data().amount()).isEqualTo(270_000L);
    }

    private void givenLedgerSaved() {
        given(walletLedgerRepository.save(any())).willAnswer(invocation -> {
            WalletLedger ledger = invocation.getArgument(0);
            ReflectionTestUtils.setField(ledger, "id", LEDGER_ID);
            return ledger;
        });
    }

    private void givenPayoutSaved() {
        given(payoutRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
    }

    private PaymentPayoutCompletedEvent publishedCompleted() {
        ArgumentCaptor<PaymentPayoutCompletedEvent> event = ArgumentCaptor.forClass(PaymentPayoutCompletedEvent.class);
        then(eventPublisher).should().publish(event.capture());
        return event.getValue();
    }

    private PaymentPayoutFailedEvent publishedFailed() {
        ArgumentCaptor<PaymentPayoutFailedEvent> event = ArgumentCaptor.forClass(PaymentPayoutFailedEvent.class);
        then(eventPublisher).should().publish(event.capture());
        return event.getValue();
    }
}
