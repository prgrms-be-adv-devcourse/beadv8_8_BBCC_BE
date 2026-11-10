package com.bbcc.kidly.shared.order.out;

import com.bbcc.kidly.shared.order.dto.SellerOrderItemSummary;

/**
 * 판매자 상품의 주문상품 조회(#1120). 주문 도메인이 구현한다. 주문은 사실(개수)만 주고, 그 값으로 무엇을 할지는 부르는 쪽이
 * 정한다. 서비스가 나뉘어 있으면 주문 서비스의 내부 경로 GET /internal/sellers/{sellerId}/order-items/summary가 같은
 * 모양으로 답한다(밖에서는 Gateway가 /internal/**을 막는다).
 */
public interface SellerOrderItems {

    /**
     * 그 판매자 상품의 미종결 주문상품 수. 주문이 없으면 0이다(없는 판매자도 0).
     *
     * @param sellerId 판매자 ID(1 이상)
     */
    SellerOrderItemSummary summarize(Long sellerId);
}
