package com.bbcc.kidly.boundedcontext.notification.app.dto;

import java.util.List;
import java.util.UUID;

/**
 * 결제완료된 주문. 구매자 주문 확인 메일과 판매자 새 주문 알림함을 만든다.
 *
 * @param eventId 주문 결제완료 이벤트 ID. 같은 이벤트로는 메일을 한 통만 만든다
 * @param memberId 주문한 회원(구매자) ID
 * @param totalAmount 주문 총액(원)
 */
public record OrderPaidCommand(UUID eventId, Long orderId, Long memberId, long totalAmount, List<Item> items) {

    /**
     * @param sellerId 판매자(회원 ID)
     * @param optionName 옵션명. 옛 주문상품은 null일 수 있다
     * @param amount 주문상품 금액(원)
     */
    public record Item(Long sellerId, String productName, String optionName, int quantity, long amount) {
    }
}
