package com.bbcc.kidly.boundedcontext.delivery.app.port.in;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 배송이 다른 컨텍스트(주문)에 내주는 주문별 배송 만들기 · 조회 · 취소(#617 · #641). 구현은 배송 app이고 부르는 쪽 트랜잭션에 합류한다.
 */
public interface DeliveryOperations {

    /**
     * 주문상품을 판매자로 묶어 판매자마다 배송 준비 1건을 만든다(이력 주체 = payment.completed 이벤트). 주문이 없으면 만들지 않는다.
     */
    void createFor(Long orderId);

    /**
     * 이벤트가 아닌 주체(관리자 재처리)가 만든다. 상태 이력에 그 주체를 남긴다.
     */
    void createFor(Long orderId, DeliveryActor actor);

    /**
     * 주문의 배송들. 만든 순서대로.
     */
    List<DeliveryView> findByOrder(Long orderId);

    /**
     * 구매자 취소로 주문의 배송들을 취소한다(발송 전만, 이미 취소면 그대로). 발송 여부는 부른 쪽이 먼저 확인한다.
     * 상태 이력은 부른 쪽(주문)이 바뀐 결과로 남긴다.
     *
     * @return 배송마다 이전 상태와 취소 뒤 배송, 만든 순서대로
     */
    List<DeliveryChange> cancelByOrder(Long orderId);

    /**
     * 다른 컨텍스트가 읽는 판매자별 배송 한 건. 상태 · 택배사는 enum 이름 문자열.
     *
     * @param shipped 발송됐는지(배송 중 · 배송 완료)
     * @param deliveredAt 배송 완료 전이면 null
     */
    record DeliveryView(
        Long deliveryId,
        Long orderId,
        Long sellerId,
        String status,
        String carrier,
        String trackingNumber,
        LocalDateTime shippedAt,
        LocalDateTime deliveredAt,
        boolean shipped) {
    }

    /**
     * 배송 상태가 바뀐 결과.
     *
     * @param from 바뀌기 전 상태 이름
     * @param delivery 바뀐 뒤 배송
     */
    record DeliveryChange(String from, DeliveryView delivery) {
    }

    /**
     * 배송 상태를 바꾼 주체. 주문 상태 이력의 주체 종류 이름(BUYER · SELLER · ADMIN · SYSTEM · EVENT) · 회원 ID · 요청 · 작업 · 이벤트 이름.
     *
     * @param id 회원 ID. 시스템 · 이벤트는 null
     */
    record DeliveryActor(String type, Long id, String source) {
    }
}
