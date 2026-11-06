package com.bbcc.kidly.boundedcontext.order.app.port;

/**
 * 결제완료된 주문의 상품을 장바구니에서 뺀다(#641). 구현은 out의 장바구니 컨텍스트 어댑터이고 부르는 쪽 트랜잭션에 합류한다.
 */
public interface OrderedCartCleaner {

    /**
     * 결제완료된 주문이 산 장바구니 칸을 지운다(#998: 주문상품의 장바구니 칸 ID가 있으면 그 칸, 없으면 구매자의 같은 (옵션, 자녀) 칸).
     * 장바구니는 주문을 읽지 않으므로 지울 칸 정보는 이 쪽(주문)이 넘긴다.
     */
    void clearOrdered(Long orderId);
}
