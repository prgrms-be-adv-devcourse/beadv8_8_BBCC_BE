package com.bbcc.kidly.shared.order.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 주문이 결제완료됐다(#899). 결제대기 주문이 결제완료로 실제로 바뀔 때 한 번만 나간다(같은 결제 완료가 다시 와도 · 이미 결제완료 ·
 * 취소된 주문에 온 결제 완료는 나가지 않는다). 결제 완료 이벤트 수신과 관리자 결제 복구 재처리가 모두 같은 반영을 거친다.
 * 상품명 · 옵션명 · 판매자 · 상품 ID는 주문할 때 저장한 스냅샷이다(지금 상품 정보가 아니다). 배송지 · 자녀 정보는 넣지 않는다(개인정보).
 * 대상이 주문이라 {@code aggregateId}는 주문 ID이고, {@code correlationId}는 기본값(주문 ID)이라 같은 주문의 다른 주문 이벤트와 순서가 지켜진다.
 */
public record OrderPaidEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    /**
     * @param orderId 주문 ID
     * @param memberId 주문한 회원 ID
     * @param paidAt 결제완료로 바뀐 시각(주문이 기록한 결제 시각)
     * @param totalAmount 주문 총액(원)
     * @param items 주문의 모든 주문상품(주문상품 순서)
     */
    public record Data(
        Long orderId,
        Long memberId,
        LocalDateTime paidAt,
        long totalAmount,
        List<Item> items) {
    }

    /**
     * @param orderItemId 주문상품 ID
     * @param sellerId 판매자(회원 ID). 주문할 때의 스냅샷
     * @param productId 상품 ID. 주문할 때의 스냅샷
     * @param productName 주문할 때의 상품명
     * @param optionName 주문할 때의 옵션명(예: "네이비 / 110"). 옵션명을 저장하지 않던 옛 주문상품은 null일 수 있다
     * @param quantity 수량
     * @param amount 주문상품 금액(원)
     */
    public record Item(
        Long orderItemId,
        Long sellerId,
        Long productId,
        String productName,
        String optionName,
        int quantity,
        long amount) {
    }

    @Override
    public String aggregateId() {
        return String.valueOf(data.orderId());
    }
}
