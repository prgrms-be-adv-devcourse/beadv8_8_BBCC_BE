package com.bbcc.kidly.shared.order.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 보호자가 조르기를 승인했다(#702). 대상은 조르기라 {@code aggregateId}는 조르기 ID다.
 * 특정 받는 도메인에 맞추지 않고 사실만 담는다(문구 · 개인정보 없음).
 * items의 productId는 주문이 받아 둔 상품 복사본에서 읽으며, 상품 복사본이 없으면 null이다(optionId는 늘 있다).
 * 같은 자녀의 조르기 흐름 순서를 지키도록 {@code correlationId}(키)는 자녀 프로필 ID다.
 * 클래스 이름 규칙으로 {@code eventType}은 {@code wish.approved},
 * 발행 도메인은 주문(조르기는 주문 도메인 안의 컨텍스트)이다.
 * #999부터 발행하지 않는다(조르기 재설계). shared 정리 때 지운다.
 */
public record WishApprovedEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    /**
     * @param childId 자녀 프로필 ID
     * @param memberId 그 자녀의 보호자 회원 ID
     * @param items 승인한 상품만(quantity = 승인 수량)
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
