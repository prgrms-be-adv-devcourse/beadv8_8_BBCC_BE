package com.bbcc.kidly.shared.order.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 장바구니에 상품을 담았다(새 줄 · 같은 줄 수량 합치기 모두, #654). 특정 받는 도메인에 맞추지 않고 담았다는 사실만 담는다.
 * 대상이 장바구니 줄이라 {@code aggregateId}는 장바구니 줄 ID, 같은 회원의 담기 순서를 지키도록 {@code correlationId}는 회원 ID다.
 * 클래스 이름 규칙으로 {@code eventType}은 {@code cart.item-added}, 발행 도메인은 주문(장바구니는 주문 도메인 안의 컨텍스트)이다.
 */
public record CartItemAddedEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    /**
     * @param childId 자녀를 고르지 않았으면 null
     * @param quantity 이번에 담은 수량
     * @param cartQuantity 담은 뒤 그 장바구니 줄의 수량. 새 줄이면 quantity와 같다
     */
    public record Data(
        Long cartItemId,
        Long memberId,
        Long childId,
        Long productId,
        Long optionId,
        int quantity,
        int cartQuantity) {
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
