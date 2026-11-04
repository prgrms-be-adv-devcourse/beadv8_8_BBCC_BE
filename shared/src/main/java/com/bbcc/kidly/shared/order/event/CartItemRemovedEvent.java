package com.bbcc.kidly.shared.order.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 사용자가 장바구니 줄을 뺐다(#709). 특정 받는 도메인에 맞추지 않고 뺐다는 사실만 담는다. 결제가 끝나 장바구니가 정리된 것(산 것)과
 * 수량 변경은 이 이벤트가 아니다. 담기({@link CartItemAddedEvent})와 같이 {@code aggregateId}는 장바구니 줄 ID,
 * 같은 회원의 담기 · 빼기 순서를 지키도록 {@code correlationId}는 회원 ID다.
 * 클래스 이름 규칙으로 {@code eventType}은 {@code cart.item-removed}, 발행 도메인은 주문(장바구니는 주문 도메인 안의 컨텍스트)이다.
 */
public record CartItemRemovedEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    /**
     * @param childId 자녀를 고르지 않았으면 null
     * @param productId 상품 ID. 주문이 받아 둔 상품 정보가 없으면 null
     * @param quantity 뺄 때 그 줄의 수량
     */
    public record Data(
        Long cartItemId,
        Long memberId,
        Long childId,
        Long productId,
        Long optionId,
        int quantity) {
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
