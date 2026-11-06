package com.bbcc.kidly.shared.order.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 결제가 끝난 주문이 산 장바구니 줄을 정리했다(#998). 특정 받는 도메인에 맞추지 않고 산 줄이 장바구니에서 빠졌다는 사실만 담는다.
 * 사용자가 뺀 것은 {@link CartItemRemovedEvent}다. 담기 · 빼기와 같이 {@code aggregateId}는 장바구니 줄 ID,
 * 같은 회원의 장바구니 이벤트 순서를 지키도록 {@code correlationId}는 (결제) 회원 ID다.
 * 클래스 이름 규칙으로 {@code eventType}은 {@code cart.item-purchased}, 발행 도메인은 주문(장바구니는 주문 도메인 안의 컨텍스트)이다.
 */
public record CartItemPurchasedEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    /**
     * @param cartItemId 정리된 장바구니 줄 ID
     * @param ownerType 장바구니 주인 종류 MEMBER · CHILD
     * @param ownerId 장바구니 주인 ID: 회원 ID 또는 자녀 프로필 ID
     * @param memberId 결제한 회원 ID(자녀 장바구니면 그 자녀의 보호자)
     * @param optionId 상품 옵션 ID
     * @param childId 입을 자녀. 고르지 않았으면 null
     * @param quantity 정리할 때 그 줄의 수량
     * @param orderId 그 줄을 산 주문 ID
     * @param orderItemId 그 줄을 산 주문상품 ID. 모르면 null
     * @param reservationId 예약된 줄이었으면 그 예약 ID(예약한 쪽이 준 불투명 문자열), 아니면 null
     */
    public record Data(
        Long cartItemId,
        String ownerType,
        Long ownerId,
        Long memberId,
        Long optionId,
        Long childId,
        int quantity,
        Long orderId,
        Long orderItemId,
        String reservationId) {
    }

    @Override
    public String aggregateId() {
        return String.valueOf(data.cartItemId());
    }

    @Override
    public String correlationId() {
        return String.valueOf(data.memberId());
    }
}
