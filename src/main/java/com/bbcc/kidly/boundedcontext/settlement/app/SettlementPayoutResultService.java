package com.bbcc.kidly.boundedcontext.settlement.app;

import com.bbcc.kidly.boundedcontext.settlement.domain.Settlement;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRepository;
import com.bbcc.kidly.shared.payment.event.PaymentPayoutCompletedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentPayoutFailedEvent;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제의 지급 결과를 정산서에 반영한다. 정산서가 없거나 판매자 · 금액이 다른 결과는 반영하지 않고 ERROR로 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementPayoutResultService {

    private final SettlementRepository settlementRepository;

    @Transactional
    public void markPaid(PaymentPayoutCompletedEvent.Data data) {
        findMatching(data.settlementId(), data.sellerId(), data.amount())
            .ifPresent(settlement -> settlement.markPaid(data.paidAt()));
    }

    @Transactional
    public void markPaymentFailed(PaymentPayoutFailedEvent.Data data) {
        findMatching(data.settlementId(), data.sellerId(), data.amount())
            .ifPresent(settlement -> settlement.markPaymentFailed(data.reasonCode().name()));
    }

    private Optional<Settlement> findMatching(Long settlementId, Long sellerId, long amount) {
        var found = settlementRepository.findById(settlementId);
        if (found.isEmpty()) {
            log.error("지급 결과의 정산서가 없습니다: settlementId={}", settlementId);
            return Optional.empty();
        }
        var settlement = found.get();
        if (!settlement.isPayoutOf(sellerId, amount)) {
            log.error("지급 결과가 정산서와 다릅니다: settlementId={}, 받은 sellerId={} amount={}, 정산서 sellerId={} amount={}",
                settlementId, sellerId, amount, settlement.getSellerId(), settlement.getSettlementAmount());
            return Optional.empty();
        }
        return found;
    }
}
