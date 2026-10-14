package com.bbcc.kidly.boundedcontext.payment.app;

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
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정산금액을 판매자 예치금에 입금하고 결과를 payment.payout-completed · payment.payout-failed로 알린다.
 * 정산서당 입금은 한 번이다. 지갑을 락으로 읽은 뒤 그 정산서의 SETTLEMENT 원장이 있는지 보므로, 같은 요청이 동시에 와도
 * 늦은 쪽은 앞 입금을 보고 건너뛴다. 원장 {@code (type, ref_id)} UK가 마지막으로 막는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementPayoutService {

    private final WalletRepository walletRepository;
    private final WalletLedgerRepository walletLedgerRepository;
    private final EventPublisher eventPublisher;

    @Transactional
    public void payout(PayoutCommand command) {
        LocalDateTime now = LocalDateTime.now();
        walletRepository.findWithLockByMemberId(command.sellerId()).ifPresentOrElse(
            wallet -> {
                if (walletLedgerRepository.existsByTypeAndRefId(WalletLedgerType.SETTLEMENT, command.statementId())) {
                    return;
                }
                publishCompleted(command, deposit(command, wallet), now);
            },
            () -> {
                log.error("정산금액을 입금할 판매자의 지갑이 없습니다. statementId={}, sellerId={}",
                    command.statementId(), command.sellerId());
                eventPublisher.publish(new PaymentPayoutFailedEvent(UUID.randomUUID(), now,
                    new PaymentPayoutFailedEvent.Data(command.statementId(), command.sellerId(), command.amount(),
                        PayoutFailureCode.WALLET_NOT_FOUND, now)));
            });
    }

    // 입금한 원장 ID. 0원이면 입금하지 않아 null
    private Long deposit(PayoutCommand command, Wallet wallet) {
        if (command.amount() == 0) {
            return null;
        }
        wallet.deposit(command.amount());
        return walletLedgerRepository.save(WalletLedger.record(wallet.getId(), WalletLedgerType.SETTLEMENT,
            command.amount(), wallet.getBalance(), command.statementId())).getId();
    }

    private void publishCompleted(PayoutCommand command, Long ledgerId, LocalDateTime now) {
        eventPublisher.publish(new PaymentPayoutCompletedEvent(UUID.randomUUID(), now,
            new PaymentPayoutCompletedEvent.Data(command.statementId(), command.sellerId(), command.amount(),
                ledgerId, now)));
    }
}
