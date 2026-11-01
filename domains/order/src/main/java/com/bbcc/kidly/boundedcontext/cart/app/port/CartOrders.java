package com.bbcc.kidly.boundedcontext.cart.app.port;

import java.util.List;

/**
 * 주문한 상품(#641). 구현은 out의 주문 컨텍스트 어댑터이고 부르는 쪽 트랜잭션에 합류한다.
 */
public interface CartOrders {

    /**
     * 주문의 주문상품마다 (회원, 옵션, 자녀). 주문이 없으면 빈 목록.
     */
    List<OrderedLine> findOrderedLines(Long orderId);

    /**
     * @param childId 자녀를 고르지 않았으면 null
     */
    record OrderedLine(Long memberId, Long optionId, Long childId) {
    }
}
