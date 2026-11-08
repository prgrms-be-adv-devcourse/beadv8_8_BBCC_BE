package com.bbcc.kidly.boundedcontext.order.app.port;

import java.util.List;

/**
 * 주문하기가 장바구니 칸으로 사도 되는지 장바구니에 묻는다(#998). 구현은 out의 장바구니 컨텍스트 어댑터이고 부르는 쪽 트랜잭션에 합류한다.
 */
public interface OrderCartLines {

    /**
     * 칸마다 구매자 칸이거나 구매자가 허용된 예약 칸이고 옵션 · 자녀가 같아야 한다(예약 칸은 주문 수량 ≤ 칸 수량). 아니면 장바구니의
     * 업무 예외(403 · 404 · 409)가 그대로 나간다.
     *
     * @param memberId 구매자 회원 ID
     */
    void requireOrderable(Long memberId, List<Line> lines);

    /**
     * @param childId 입을 자녀. 없으면 null
     * @param quantity 이 칸으로 주문하는 수량
     */
    record Line(Long cartItemId, Long optionId, Long childId, int quantity) {
    }
}
