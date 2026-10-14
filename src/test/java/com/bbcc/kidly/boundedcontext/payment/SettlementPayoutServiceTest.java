package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.WALLET_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.bbcc.kidly.boundedcontext.payment.app.SettlementPayoutService;
import com.bbcc.kidly.boundedcontext.payment.app.dto.PayoutCommand;
import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedger;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerType;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.payment.domain.PayoutFailureCode;
import com.bbcc.kidly.shared.payment.event.PaymentPayoutCompletedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentPayoutFailedEvent;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SettlementPayoutServiceTest {

    private static final Long STATEMENT_ID = 31L;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletLedgerRepository walletLedgerRepository;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private SettlementPayoutService settlementPayoutService;

    @Test
    @DisplayName("판매자 지갑에 정산금액을 입금하고 원장 SETTLEMENT(ref_id = 정산서 ID)를 남긴 뒤 payment.payout-completed를 발행한다")
    void payout() {
        Wallet wallet = PaymentFixture.savedWallet(5_000L);
        given(walletRepository.findWithLockByMemberId(MEMBER_ID)).willReturn(Optional.of(wallet));
        given(walletLedgerRepository.save(any())).willAnswer(invocation -> {
            WalletLedger ledger = invocation.getArgument(0);
            ReflectionTestUtils.setField(ledger, "id", 4001L);
            return ledger;
        });

        settlementPayoutService.payout(new PayoutCommand(STATEMENT_ID, MEMBER_ID, 270_000L));

        assertThat(wallet.getBalance()).isEqualTo(275_000L);
        ArgumentCaptor<WalletLedger> ledger = ArgumentCaptor.forClass(WalletLedger.class);
        then(walletLedgerRepository).should().save(ledger.capture());
        assertThat(ledger.getValue().getWalletId()).isEqualTo(WALLET_ID);
        assertThat(ledger.getValue().getType()).isEqualTo(WalletLedgerType.SETTLEMENT);
        assertThat(ledger.getValue().getAmount()).isEqualTo(270_000L);
        assertThat(ledger.getValue().getBalanceAfter()).isEqualTo(275_000L);
        assertThat(ledger.getValue().getRefId()).isEqualTo(STATEMENT_ID);
        ArgumentCaptor<PaymentPayoutCompletedEvent> event = ArgumentCaptor.forClass(PaymentPayoutCompletedEvent.class);
        then(eventPublisher).should().publish(event.capture());
        assertThat(event.getValue().data().statementId()).isEqualTo(STATEMENT_ID);
        assertThat(event.getValue().data().sellerId()).isEqualTo(MEMBER_ID);
        assertThat(event.getValue().data().amount()).isEqualTo(270_000L);
        assertThat(event.getValue().data().ledgerId()).isEqualTo(4001L);
        assertThat(event.getValue().data().paidAt()).isNotNull();
    }

    @Test
    @DisplayName("0원 정산이면 입금 · 원장 없이 ledgerId가 null인 payment.payout-completed를 발행한다")
    void payoutZero() {
        Wallet wallet = PaymentFixture.savedWallet(5_000L);
        given(walletRepository.findWithLockByMemberId(MEMBER_ID)).willReturn(Optional.of(wallet));

        settlementPayoutService.payout(new PayoutCommand(STATEMENT_ID, MEMBER_ID, 0L));

        assertThat(wallet.getBalance()).isEqualTo(5_000L);
        then(walletLedgerRepository).should(never()).save(any());
        ArgumentCaptor<PaymentPayoutCompletedEvent> event = ArgumentCaptor.forClass(PaymentPayoutCompletedEvent.class);
        then(eventPublisher).should().publish(event.capture());
        assertThat(event.getValue().data().amount()).isZero();
        assertThat(event.getValue().data().ledgerId()).isNull();
    }

    @Test
    @DisplayName("같은 정산서의 입금 원장이 이미 있으면 입금하지 않고 발행도 하지 않는다")
    void alreadyPaid() {
        Wallet wallet = PaymentFixture.savedWallet(5_000L);
        given(walletRepository.findWithLockByMemberId(MEMBER_ID)).willReturn(Optional.of(wallet));
        given(walletLedgerRepository.existsByTypeAndRefId(WalletLedgerType.SETTLEMENT, STATEMENT_ID)).willReturn(true);

        settlementPayoutService.payout(new PayoutCommand(STATEMENT_ID, MEMBER_ID, 270_000L));

        assertThat(wallet.getBalance()).isEqualTo(5_000L);
        then(walletLedgerRepository).should(never()).save(any());
        then(eventPublisher).should(never()).publish(any());
    }

    @Test
    @DisplayName("판매자 지갑이 없으면 payment.payout-failed(WALLET_NOT_FOUND)를 발행한다")
    void walletNotFound() {
        given(walletRepository.findWithLockByMemberId(MEMBER_ID)).willReturn(Optional.empty());

        settlementPayoutService.payout(new PayoutCommand(STATEMENT_ID, MEMBER_ID, 270_000L));

        then(walletLedgerRepository).should(never()).save(any());
        ArgumentCaptor<PaymentPayoutFailedEvent> event = ArgumentCaptor.forClass(PaymentPayoutFailedEvent.class);
        then(eventPublisher).should().publish(event.capture());
        assertThat(event.getValue().data().statementId()).isEqualTo(STATEMENT_ID);
        assertThat(event.getValue().data().amount()).isEqualTo(270_000L);
        assertThat(event.getValue().data().reasonCode()).isEqualTo(PayoutFailureCode.WALLET_NOT_FOUND);
        assertThat(event.getValue().data().failedAt()).isNotNull();
    }
}
