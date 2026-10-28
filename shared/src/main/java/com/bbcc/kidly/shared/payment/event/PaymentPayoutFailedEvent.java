package com.bbcc.kidly.shared.payment.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.payment.domain.PayoutFailureCode;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 정산금액을 판매자 예치금에 입금하지 못했다. 정산 도메인이 정산서를 지급 실패로 바꾼다.
 * 같은 정산서로 다시 요청하면 입금을 다시 시도하지 않고 이 이벤트를 다시 발행한다. 원인은 운영자가 처리한다.
 * 받는 쪽은 같은 이벤트를 다시 받아도 한 번만 반영한다.
 * 대상이 정산서라 {@code aggregateId}와 {@code correlationId}가 모두 정산서 ID다.
 */
public record PaymentPayoutFailedEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    /**
     * @param settlementId 정산서 ID
     * @param sellerId 판매자 ID(회원 ID)
     * @param amount 입금하려던 금액(원)
     */
    public record Data(
        Long settlementId,
        Long sellerId,
        long amount,
        PayoutFailureCode reasonCode,
        LocalDateTime failedAt) {
    }

    @Override
    public String aggregateId() {
        return String.valueOf(data.settlementId());
    }
}
