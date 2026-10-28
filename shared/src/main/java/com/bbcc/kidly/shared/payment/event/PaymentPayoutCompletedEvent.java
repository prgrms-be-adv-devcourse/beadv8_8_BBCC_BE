package com.bbcc.kidly.shared.payment.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 정산금액을 판매자 예치금에 입금했다. 정산 도메인이 정산서를 지급 완료로 바꾼다.
 * 같은 정산서의 지급 요청이 다시 오면 입금하지 않고 처음 결과(원장 ID · 지급 시각)로 이 이벤트를 다시 발행한다(0원 포함).
 * 받는 쪽은 같은 이벤트를 다시 받아도 한 번만 반영한다.
 * 대상이 정산서라 {@code aggregateId}와 {@code correlationId}가 모두 정산서 ID다.
 */
public record PaymentPayoutCompletedEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    /**
     * @param sellerId 판매자 ID(회원 ID)
     * @param amount 입금한 금액(원)
     * @param settlementId 정산서 ID
     * @param ledgerId 입금을 기록한 원장 ID. 0원 정산이면 입금이 없어 null
     * @param paidAt 결제가 지급 완료로 처리한 시각. 0원 정산도 같은 기준이다
     */
    public record Data(
        Long settlementId,
        Long sellerId,
        long amount,
        Long ledgerId,
        LocalDateTime paidAt) {
    }

    @Override
    public String aggregateId() {
        return String.valueOf(data.settlementId());
    }
}
