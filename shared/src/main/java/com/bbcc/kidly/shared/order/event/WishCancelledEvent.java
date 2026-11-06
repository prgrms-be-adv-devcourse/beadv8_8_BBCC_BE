package com.bbcc.kidly.shared.order.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 조르기가 처리 전에 철회됐다: 보낸 사람이 직접(#968) 또는 자녀 · 계정 삭제 · 시스템(원인 {@code cancelReason}, #999).
 * 대상은 조르기라 {@code aggregateId}는 조르기 ID다.
 * 다른 조르기 이벤트(wish.sent · wish.rejected 등)와 같은 모양이고, 특정 받는 도메인에 맞추지 않고 사실만 담는다(문구 · 개인정보 없음).
 * items의 productId는 주문이 받아 둔 상품 복사본에서 읽으며, 상품 복사본이 없으면 null이다(optionId는 늘 있다).
 * 같은 자녀의 조르기 흐름 순서를 지키도록 {@code correlationId}(키)는 자녀 프로필 ID다.
 * 클래스 이름 규칙으로 {@code eventType}은 {@code wish.cancelled},
 * 발행 도메인은 주문(조르기는 주문 도메인 안의 컨텍스트)이다.
 */
public record WishCancelledEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    /**
     * @param childId 자녀 프로필 ID
     * @param memberId 그 자녀의 보호자 회원 ID
     * @param items 조른 상품(quantity = 조른 수량)
     * @param cancelReason 철회 원인 DIRECT(보낸 사람이 직접) · CHILD_DELETED · ACCOUNT_DELETED · SYSTEM(#999, 추가 칸).
     *                     없는 예전 메시지는 DIRECT로 읽는다
     */
    public record Data(
        Long wishRequestId,
        Long childId,
        Long memberId,
        List<Item> items,
        String cancelReason) {

        /**
         * 원인 칸이 없던 예전 모양(#968).
         */
        public Data(Long wishRequestId, Long childId, Long memberId, List<Item> items) {
            this(wishRequestId, childId, memberId, items, null);
        }
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
