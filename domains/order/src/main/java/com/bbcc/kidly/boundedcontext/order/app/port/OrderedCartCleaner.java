package com.bbcc.kidly.boundedcontext.order.app.port;

/**
 * 결제완료된 주문의 상품을 장바구니에서 뺀다(#641). 구현은 out의 장바구니 컨텍스트 어댑터이고 부르는 쪽 트랜잭션에 합류한다.
 */
public interface OrderedCartCleaner {

    /**
     * 주문의 (회원, 옵션, 자녀)와 같은 장바구니 줄을 수량과 상관없이 통째로 지운다.
     */
    void clearOrdered(Long orderId);
}
