package com.bbcc.kidly.boundedcontext.cart.app.port.in;

/**
 * 장바구니가 다른 컨텍스트(주문)에 내주는 정리(#617 · #641). 구현은 장바구니 app이고 부르는 쪽 트랜잭션에 합류한다.
 */
public interface CartCleanup {

    /**
     * 결제완료된 주문의 (회원, 옵션, 자녀)와 같은 장바구니 줄을 수량과 상관없이 통째로 지운다.
     */
    void clearOrdered(Long orderId);
}
