package com.bbcc.kidly.shared.order.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 조르기를 보냈다(#702). 대상은 조르기라 {@code aggregateId}는 조르기 ID다.
 * 특정 받는 도메인에 맞추지 않고 사실만 담는다(문구 · 개인정보 없음).
 * 같은 자녀의 조르기 흐름 순서를 지키도록 {@code correlationId}(키)는 자녀 프로필 ID다.
 * 클래스 이름 규칙으로 {@code eventType}은 {@code wish.sent},
 * 발행 도메인은 주문(조르기는 주문 도메인 안의 컨텍스트)이다.
 */
public record WishSentEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    /**
     * @param childId 자녀 프로필 ID
     * @param memberId 그 자녀의 보호자 회원 ID
     * @param items 조른 상품(quantity = 조른 수량)
     */
    public record Data(
        Long wishRequestId,
        Long childId,
        Long memberId,
        List<Item> items) {
    }

    /**
     * @param productId 상품 ID. 주문이 받아 둔 상품 정보가 없으면 null
     */
    public record Item(Long productId, Long optionId, int quantity) {
    }

    @Override
    public String aggregateId() {
        return String.valueOf(data.wishRequestId());
    }

    @Override
    public String correlationId() {
        return String.valueOf(data.childId());
    }
}
