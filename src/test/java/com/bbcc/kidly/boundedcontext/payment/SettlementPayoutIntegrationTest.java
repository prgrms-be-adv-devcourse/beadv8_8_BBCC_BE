package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.MEMBER_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.payment.domain.Payout;
import com.bbcc.kidly.boundedcontext.payment.domain.PayoutStatus;
import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerType;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.boundedcontext.payment.out.JpaPayoutRepository;
import com.bbcc.kidly.boundedcontext.payment.out.JpaWalletLedgerRepository;
import com.bbcc.kidly.boundedcontext.payment.out.JpaWalletRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.payment.domain.PayoutFailureCode;
import com.bbcc.kidly.shared.payment.event.PaymentPayoutCompletedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentPayoutFailedEvent;
import com.bbcc.kidly.shared.settlement.event.SettlementPayoutRequestedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * settlement.payout-requested 커밋 → 판매자 예치금 입금 · 원장 저장 → payment.payout-completed · payment.payout-failed.
 * 리스너는 발행한 트랜잭션이 커밋된 뒤에 실행되므로 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
@RecordApplicationEvents
class SettlementPayoutIntegrationTest {

    private static final Long SETTLEMENT_ID = 31L;

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private JpaWalletRepository jpaWalletRepository;

    @Autowired
    private JpaWalletLedgerRepository jpaWalletLedgerRepository;

    @Autowired
    private JpaPayoutRepository jpaPayoutRepository;

    @Test
    @DisplayName("지급 요청이면 입금하고 원장 · 지급 기록을 남긴 뒤 완료를 발행한다, 다시 오면 입금 없이 같은 결과를 다시 발행한다")
    void payoutOnce() {
        Wallet wallet = walletRepository.save(PaymentFixture.wallet(5_000L));
        SettlementPayoutRequestedEvent event = payoutRequested(270_000L);

        publishInTransaction(event);
        publishInTransaction(event);

        assertThat(jpaWalletRepository.findById(wallet.getId())).get()
            .extracting(Wallet::getBalance).isEqualTo(275_000L);
        assertThat(jpaWalletLedgerRepository.findAll()).singleElement().satisfies(ledger -> {
            assertThat(ledger.getType()).isEqualTo(WalletLedgerType.SETTLEMENT);
            assertThat(ledger.getAmount()).isEqualTo(270_000L);
            assertThat(ledger.getBalanceAfter()).isEqualTo(275_000L);
            assertThat(ledger.getRefId()).isEqualTo(SETTLEMENT_ID);
        });
        Long ledgerId = jpaWalletLedgerRepository.findAll().get(0).getId();
        assertThat(jpaPayoutRepository.findAll()).singleElement().satisfies(payout -> {
            assertThat(payout.getStatus()).isEqualTo(PayoutStatus.COMPLETED);
            assertThat(payout.getLedgerId()).isEqualTo(ledgerId);
        });
        List<PaymentPayoutCompletedEvent> completed =
            events.stream(PaymentPayoutCompletedEvent.class).toList();
        assertThat(completed).hasSize(2).allSatisfy(each -> {
            assertThat(each.data().settlementId()).isEqualTo(SETTLEMENT_ID);
            assertThat(each.data().sellerId()).isEqualTo(MEMBER_ID);
            assertThat(each.data().amount()).isEqualTo(270_000L);
            assertThat(each.data().ledgerId()).isEqualTo(ledgerId);
        });
        assertThat(completed.get(1).data().paidAt()).isEqualToIgnoringNanos(completed.get(0).data().paidAt());
    }

    @Test
    @DisplayName("판매자 지갑이 없으면 입금하지 않고 지급 실패를 기록한 뒤 payment.payout-failed만 발행한다")
    void walletNotFound() {
        publishInTransaction(payoutRequested(270_000L));

        assertThat(jpaWalletLedgerRepository.findAll()).isEmpty();
        assertThat(jpaPayoutRepository.findAll()).singleElement()
            .extracting(Payout::getStatus).isEqualTo(PayoutStatus.FAILED);
        assertThat(events.stream(PaymentPayoutFailedEvent.class).toList()).singleElement().satisfies(failed -> {
            assertThat(failed.data().settlementId()).isEqualTo(SETTLEMENT_ID);
            assertThat(failed.data().reasonCode()).isEqualTo(PayoutFailureCode.WALLET_NOT_FOUND);
        });
    }

    @Test
    @DisplayName("지갑이 없어 실패한 정산서는 지갑이 생긴 뒤 다시 요청이 와도 입금하지 않고 실패를 다시 발행한다")
    void failedIsFinal() {
        publishInTransaction(payoutRequested(270_000L));
        Wallet wallet = walletRepository.save(PaymentFixture.wallet(0L));

        publishInTransaction(payoutRequested(270_000L));

        assertThat(jpaWalletRepository.findById(wallet.getId())).get()
            .extracting(Wallet::getBalance).isEqualTo(0L);
        assertThat(jpaPayoutRepository.findAll()).singleElement()
            .extracting(Payout::getStatus).isEqualTo(PayoutStatus.FAILED);
        assertThat(events.stream(PaymentPayoutFailedEvent.class).toList()).hasSize(2);
        assertThat(events.stream(PaymentPayoutCompletedEvent.class).toList()).isEmpty();
    }

    private void publishInTransaction(SettlementPayoutRequestedEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }

    private static SettlementPayoutRequestedEvent payoutRequested(long amount) {
        return new SettlementPayoutRequestedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new SettlementPayoutRequestedEvent.Data(
                SETTLEMENT_ID, MEMBER_ID, amount, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));
    }
}
