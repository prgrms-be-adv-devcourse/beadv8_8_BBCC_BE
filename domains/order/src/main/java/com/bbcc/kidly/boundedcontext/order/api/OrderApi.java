package com.bbcc.kidly.boundedcontext.order.api;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 주문이 다른 컨텍스트(장바구니 · 배송)에 내주는 주문 조회 · 배송 반영(#617). 구현은 주문 app이고 부르는 쪽 트랜잭션에 합류한다.
 */
public interface OrderApi {

    /**
     * 주문(주문상품 포함). 없으면 빈 값.
     */
    Optional<OrderView> findOrder(Long orderId);

    /**
     * 주문(주문상품 포함)들을 한 번에 읽는다. 없는 ID는 빠진다.
     */
    List<OrderView> findOrdersWithItems(Collection<Long> orderIds);

    /**
     * 그 판매자 몫 주문상품에 배송 완료 시각을 남긴다. 주문이 없으면 아무것도 하지 않는다.
     */
    void recordDelivered(Long orderId, Long sellerId, LocalDateTime deliveredAt);

    /**
     * 배송 상태가 바뀐 이력 한 줄을 주문 상태 이력에 남긴다.
     *
     * @param from 이전 상태 이름. 새로 만들었으면 null
     */
    void recordDeliveryHistory(Long orderId, Long deliveryId, HistoryActor actor, String from, String to,
        String reason, LocalDateTime occurredAt);
}
