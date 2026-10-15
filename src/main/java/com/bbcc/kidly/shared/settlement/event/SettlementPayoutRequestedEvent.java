package com.bbcc.kidly.shared.settlement.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 확정된 정산서의 지급을 요청한다. 결제 도메인이 판매자 예치금에 정산금액을 입금하고
 * {@code payment.payout-completed} · {@code payment.payout-failed}로 결과를 알린다.
 * 같은 정산서는 한 번만 입금한다. 이미 처리한 정산서를 다시 보내면 결제는 입금 없이 저장된 결과를 다시 보낸다.
 * 대상이 정산서라 {@code aggregateId}와 {@code correlationId}가 모두 정산서 ID다.
 */
public record SettlementPayoutRequestedEvent(UUID eventId, LocalDateTime occurredAt, Data data)
    implements DomainEvent {

    /**
     * @param settlementId 정산서 ID
     * @param sellerId 판매자 ID. 회원 ID와 같고, 이 회원의 예치금 지갑에 입금한다
     * @param amount 정산금액(원). 0 이상이고, 0원이면 입금 없이 완료된다
     * @param periodStartOn 정산 기간 시작일(그 달 1일)
     * @param periodEndOn 정산 기간 종료일(그 달 마지막 날)
     */
    public record Data(
        Long settlementId,
        Long sellerId,
        long amount,
        LocalDate periodStartOn,
        LocalDate periodEndOn) {
    }

    @Override
    public String aggregateId() {
        return String.valueOf(data.settlementId());
    }
}
