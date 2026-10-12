package com.bbcc.kidly.shared.member.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.member.domain.SellerStatus;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 판매자가 승인되거나 상호명이 바뀌거나 정지됐다. 상품 · 주문 · 추천 도메인이 판매자 복사본을 갱신한다.
 * 저장된 값보다 {@code occurredAt}이 늦을 때만 반영한다. 정지된 판매자의 상품 처리는 상품 도메인이 정한다.
 *
 * @param sellerId 판매자 ID. 회원 ID와 같다
 */
public record SellerChangedEvent(
    UUID eventId,
    Long sellerId,
    String businessName,
    SellerStatus status,
    LocalDateTime occurredAt) implements DomainEvent {

    @Override
    public String aggregateId() {
        return String.valueOf(sellerId);
    }
}
