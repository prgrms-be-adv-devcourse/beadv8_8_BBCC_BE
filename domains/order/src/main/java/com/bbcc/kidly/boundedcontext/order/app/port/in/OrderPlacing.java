package com.bbcc.kidly.boundedcontext.order.app.port.in;

import java.util.Collection;
import java.util.List;

/**
 * 다른 컨텍스트(조르기)가 회원 대신 주문하기를 부르고, 그 주문이 결제됐는지 읽는다(#701 · #641). 구현은 주문 app이고 부르는 쪽
 * 트랜잭션에 합류한다. 주문하기는 구매자의 주문하기와 같은 길(검증 · 저장 · 이력 · order.created)이다.
 */
public interface OrderPlacing {

    /**
     * 주문하기와 같은 검증(옵션 · 자녀 없음 404, 가격 바뀜 · 판매 중 아님 · 자기 상품 · 남의 자녀 등)을 하고, 실패하면 주문하기와 같은
     * 업무 예외가 그대로 나간다.
     *
     * @param memberId 주문하는 회원
     * @return 만든 주문 ID
     */
    Long place(Long memberId, List<Item> items, ShippingAddress shippingAddress);

    /**
     * 주문들의 결제 진행 상태. 없는 ID는 빠진다.
     */
    List<PaymentState> findPaymentStates(Collection<Long> orderIds);

    /**
     * @param childId 자녀를 고르지 않으면 null
     * @param unitPrice 화면이 보여 준 단가(지금 단가와 다르면 주문하기와 같이 409)
     */
    record Item(Long optionId, Long childId, int quantity, long unitPrice) {
    }

    record ShippingAddress(String recipientName, String phone, String zipCode, String address) {
    }

    /**
     * @param status 주문 상태 이름(PENDING_PAYMENT · PAID · CANCELLED)
     * @param paid 결제완료된 적이 있는지(결제 시각이 있음). 결제 뒤 취소된 주문도 true
     */
    record PaymentState(Long orderId, String status, boolean paid) {
    }
}
