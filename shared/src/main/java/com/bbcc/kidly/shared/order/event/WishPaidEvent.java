package com.bbcc.kidly.shared.order.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 조르기가 결제됐다(#999, 승인 = 결제): 승인자가 조르기로 예약된 장바구니 칸으로 주문해 결제가 끝났다. 대상은 조르기라
 * {@code aggregateId}는 조르기 ID다. 한 조르기에 한 번만 나간다(같은 주문의 칸이 여럿이어도, 다시 받아도).
 * 다른 조르기 이벤트(wish.sent · wish.rejected 등)와 같은 모양이고, 특정 받는 도메인에 맞추지 않고 사실만 담는다(문구 · 개인정보 없음).
 * items의 productId는 보낸 때 상품 복사본 값이며 없으면 null이다(optionId는 늘 있다).
 * 같은 자녀의 조르기 흐름 순서를 지키도록 {@code correlationId}(키)는 자녀 프로필 ID다.
 * 클래스 이름 규칙으로 {@code eventType}은 {@code wish.paid},
 * 발행 도메인은 주문(조르기는 주문 도메인 안의 컨텍스트)이다.
 */
public record WishPaidEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    /**
     * @param childId 자녀 프로필 ID
     * @param memberId 그 자녀의 보호자 회원 ID
     * @param items 조른 상품(quantity = 조른 수량). 산 칸 · 수량은 주문이 갖는다
     * @param payerType 결제한 사람 종류(MEMBER · CHILD)
     * @param payerId 결제한 사람 ID(회원 ID 또는 자녀 프로필 ID)
     * @param orderId 처음 결제를 알린 주문 ID
     */
    public record Data(
        Long wishRequestId,
        Long childId,
        Long memberId,
        List<Item> items,
        String payerType,
        Long payerId,
        Long orderId) {
    }

    /**
     * @param productId 상품 ID. 주문이 받아 둔 상품 정보가 없었으면 null
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
