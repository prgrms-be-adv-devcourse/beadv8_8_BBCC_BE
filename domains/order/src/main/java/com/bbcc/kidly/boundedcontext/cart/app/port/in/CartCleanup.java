package com.bbcc.kidly.boundedcontext.cart.app.port.in;

import java.util.List;

/**
 * 장바구니가 다른 컨텍스트(주문)에 내주는 정리(#617 · #641). 구현은 장바구니 app이고 부르는 쪽 트랜잭션에 합류한다.
 * 장바구니는 주문을 읽지 않는다(#998): 주문이 지울 칸 정보를 넘긴다.
 */
public interface CartCleanup {

    /**
     * 결제완료된 주문이 산 장바구니 칸을 수량과 상관없이 통째로 지우고 칸마다 cart.item-purchased(주문 ID · 주문상품 ID ·
     * 아직 예약 칸이면 예약 ID)를 낸다. cart.item-removed는 내지 않는다(사용자가 뺄 때만).
     * 칸 ID가 있으면(주문 때 확인된 칸) 그 칸의 결제 회원이 구매자와 같을 때 예약 상태와 상관없이
     * 지우고(예약이 먼저 풀려도 지운다), 없으면 구매자 회원의 (옵션, 입을 자녀) 칸을 지운다. 이미 없는 칸은 건너뛴다.
     */
    void clearOrdered(Long orderId, List<OrderedLine> lines);

    /**
     * @param orderItemId 그 칸을 산 주문상품 ID. 모르면 null
     * @param cartItemId 주문하기 요청에 준 장바구니 칸 ID. 없으면 null
     * @param memberId 구매자(보호자) 회원 ID
     * @param childId 입을 자녀. 없으면 null
     */
    record OrderedLine(Long orderItemId, Long cartItemId, Long memberId, Long optionId, Long childId) {
    }
}
