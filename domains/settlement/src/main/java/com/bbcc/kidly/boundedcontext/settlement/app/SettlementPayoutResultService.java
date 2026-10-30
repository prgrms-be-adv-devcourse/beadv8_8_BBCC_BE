package com.bbcc.kidly.boundedcontext.settlement.app;

import com.bbcc.kidly.boundedcontext.settlement.domain.Settlement;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRepository;
import com.bbcc.kidly.shared.payment.event.PaymentPayoutCompletedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentPayoutFailedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/**
 * 결제의 지급 결과를 정산서에 반영한다. 정산서가 없거나 판매자 · 금액이 다른 결과는 반영하지 않고 예외를 던진다(리스너가 ERROR로 남기고, Kafka 전환 뒤에는 DLT로 간다).
 */
@Service
@RequiredArgsConstructor
public class SettlementPayoutResultService {

    private final SettlementRepository settlementRepository;

    @Transactional
    public void markPaid(PaymentPayoutCompletedEvent.Data data) {
        getMatching(data.settlementId(), data.sellerId(), data.amount()).markPaid(data.paidAt());
    }

    @Transactional
    public void markPaymentFailed(PaymentPayoutFailedEvent.Data data) {
        getMatching(data.settlementId(), data.sellerId(), data.amount()).markPaymentFailed(data.reasonCode().name());
    }

    private Settlement getMatching(Long settlementId, Long sellerId, long amount) {
        var settlement = settlementRepository.findById(settlementId)
            .orElseThrow(() -> new IllegalStateException("지급 결과의 정산서가 없습니다: settlementId=" + settlementId));
        Assert.state(settlement.isPayoutOf(sellerId, amount),
            () -> "지급 결과가 정산서와 다릅니다: settlementId=%d, 받은 sellerId=%d amount=%d, 정산서 sellerId=%d amount=%d"
                .formatted(settlementId, sellerId, amount, settlement.getSellerId(), settlement.getSettlementAmount()));
        return settlement;
    }
}
