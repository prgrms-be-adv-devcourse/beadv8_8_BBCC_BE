package com.bbcc.kidly.boundedcontext.payment.app;

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
import com.bbcc.kidly.shared.payment.event.PaymentPayoutCompletedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentPayoutFailedEvent;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정산금액을 판매자 예치금에 입금하고 결과를 payment.payout-completed · payment.payout-failed로 알린다.
 * 정산서마다 지급 기록을 남겨 한 번만 입금한다. 기록이 있는 정산서로 요청이 다시 오면 입금 없이 저장된 결과를 다시 알린다.
 * 실패한 지급은 다시 시도하지 않는다. 판매자 지갑이 없으면 잔액 0으로 만들어 입금한다. 지갑을 락으로 읽은 뒤 지급 기록을 보므로 같은 요청이 동시에 와도 한 번만 입금한다.
 * 지급 기록의 정산서 ID UK와 원장 {@code (type, ref_id)} UK가 마지막으로 막는다.
 */
@Service
@RequiredArgsConstructor
public class SettlementPayoutService {

    private final WalletRepository walletRepository;
    private final WalletLedgerRepository walletLedgerRepository;
    private final PayoutRepository payoutRepository;
    private final EventPublisher eventPublisher;

    @Transactional
    public void payout(PayoutCommand command) {
        LocalDateTime now = LocalDateTime.now();
        walletRepository.createIfAbsent(command.sellerId(), now);
        Wallet wallet = walletRepository.findWithLockByMemberId(command.sellerId()).orElseThrow();
        Optional<Payout> recorded = payoutRepository.findBySettlementId(command.settlementId());
        if (recorded.isPresent()) {
            publishResult(recorded.get());
            return;
        }
        Long ledgerId = deposit(command, wallet);
        publishResult(payoutRepository.save(Payout.complete(command.settlementId(), command.sellerId(),
            command.amount(), ledgerId, now)));
    }

    // 입금한 원장 ID. 0원이면 입금하지 않아 null
    private Long deposit(PayoutCommand command, Wallet wallet) {
        if (command.amount() == 0) {
            return null;
        }
        wallet.deposit(command.amount());
        return walletLedgerRepository.save(WalletLedger.record(wallet.getId(), WalletLedgerType.SETTLEMENT,
            command.amount(), wallet.getBalance(), command.settlementId())).getId();
    }

    private void publishResult(Payout payout) {
        if (payout.getStatus() == PayoutStatus.COMPLETED) {
            eventPublisher.publish(new PaymentPayoutCompletedEvent(UUID.randomUUID(), LocalDateTime.now(),
                new PaymentPayoutCompletedEvent.Data(payout.getSettlementId(), payout.getSellerId(),
                    payout.getAmount(), payout.getLedgerId(), payout.getPaidAt())));
            return;
        }
        eventPublisher.publish(new PaymentPayoutFailedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new PaymentPayoutFailedEvent.Data(payout.getSettlementId(), payout.getSellerId(), payout.getAmount(),
                payout.getFailureCode(), payout.getFailedAt())));
    }
}
