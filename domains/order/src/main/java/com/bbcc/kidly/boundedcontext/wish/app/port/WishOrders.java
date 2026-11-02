package com.bbcc.kidly.boundedcontext.wish.app.port;

import java.util.Collection;
import java.util.List;

/**
 * 승인한 조르기를 바로 결제(주문하기)로 넘기고, 그 주문의 결제 결과를 읽는다(#701). 구현은 out의 주문 컨텍스트 어댑터이고
 * 부르는 쪽 트랜잭션에 합류한다. 주문하기가 실패하면 주문하기의 업무 예외가 그대로 나간다(조르기 승인도 함께 롤백).
 */
public interface WishOrders {

    /**
     * @param memberId 주문하는 보호자 회원 ID
     * @param childId 주문상품의 자녀(조른 자녀)
     * @return 만든 주문 ID
     */
    Long place(Long memberId, Long childId, List<Line> lines, ShippingAddress shippingAddress);

    /**
     * 없는 주문은 빠진다.
     */
    List<OrderState> findStates(Collection<Long> orderIds);

    /**
     * @param unitPrice 보호자 화면이 보여 준 단가(지금 단가와 다르면 주문하기가 409)
     */
    record Line(Long optionId, int quantity, long unitPrice) {
    }

    record ShippingAddress(String recipientName, String phone, String zipCode, String address) {
    }

    /**
     * @param paid 결제완료된 적이 있는지(결제 뒤 취소 포함)
     * @param cancelled 주문이 취소됐는지
     */
    record OrderState(Long orderId, boolean paid, boolean cancelled) {
    }
}
