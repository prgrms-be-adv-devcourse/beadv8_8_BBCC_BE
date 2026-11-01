package com.bbcc.kidly.boundedcontext.order.app.port.in;

import java.time.LocalDateTime;

/**
 * 배송 컨텍스트가 주문에 배송 결과를 남긴다(#617 · #641). 구현은 주문 app이고 부르는 쪽 트랜잭션에 합류한다.
 */
public interface DeliveryRecorder {

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

    /**
     * 상태를 바꾼 주체. 주문 상태 이력의 주체 종류 이름(BUYER · SELLER · ADMIN · SYSTEM · EVENT) · 회원 ID · 요청 · 작업 · 이벤트 이름.
     *
     * @param id 회원 ID. 시스템 · 이벤트는 null
     */
    record HistoryActor(String type, Long id, String source) {
    }
}
