package com.bbcc.kidly.shared.order.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 판매자가 배송 한 건을 발송했다(#900). 발송에 성공할 때 한 번 나간다.
 * 배송은 주문 × 판매자 단위라 한 주문에서 판매자 수만큼 나올 수 있다. 배송지 · 수령인 · 연락처는 넣지 않는다(개인정보).
 * 대상이 배송 한 건이라 {@code aggregateId}는 배송 ID, 같은 주문의 흐름을 묶도록 {@code correlationId}는 주문 ID다.
 */
public record DeliveryShippedEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    /**
     * @param deliveryId 배송 ID(판매 주문 한 건)
     * @param orderId 주문 ID
     * @param memberId 주문한 회원(구매자) ID
     * @param sellerId 이 배송의 판매자(회원 ID)
     * @param carrier 택배사 코드(CJ · EPOST · HANJIN · LOTTE · LOGEN, 개발용 DUMMY)
     * @param carrierName 택배사 화면용 이름(예: CJ대한통운)
     * @param trackingNumber 운송장 번호
     * @param shippedAt 발송 시각
     */
    public record Data(
        Long deliveryId,
        Long orderId,
        Long memberId,
        Long sellerId,
        String carrier,
        String carrierName,
        String trackingNumber,
        LocalDateTime shippedAt) {
    }

    /**
     * 대상이 배송이라 계약 이름을 명시한다.
     */
    @Override
    public String eventType() {
        return "delivery.shipped";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(data.deliveryId());
    }

    @Override
    public String correlationId() {
        return String.valueOf(data.orderId());
    }
}
