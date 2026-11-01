package com.bbcc.kidly.boundedcontext.order.app.port;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 주문의 판매자별 배송(#641). 구현은 out의 배송 컨텍스트 어댑터이고 부르는 쪽 트랜잭션에 합류한다.
 */
public interface OrderDeliveries {

    /**
     * 주문상품을 판매자로 묶어 판매자마다 배송 준비 1건을 만든다(이력 주체 = payment.completed 이벤트). 주문이 없으면 만들지 않는다.
     */
    void createFor(Long orderId);

    /**
     * 이벤트가 아닌 주체(관리자 재처리)가 만든다. 상태 이력에 그 주체를 남긴다.
     */
    void createFor(Long orderId, Actor actor);

    /**
     * 주문의 배송들. 만든 순서대로.
     */
    List<Delivery> findByOrder(Long orderId);

    /**
     * 구매자 취소로 주문의 배송들을 취소한다(발송 전만, 이미 취소면 그대로). 발송 여부는 부른 쪽이 먼저 확인한다.
     *
     * @return 배송마다 이전 상태와 취소 뒤 배송, 만든 순서대로
     */
    List<CancelledDelivery> cancelByOrder(Long orderId);

    /**
     * 발송 전(READY)이고 생성 시각 &lt; before인 배송. 생성 시각 · 배송 ID 오름차순. 목록 1번 + 건수 1번으로 읽는다.
     */
    Page<DelayedDelivery> findShippingDelayed(LocalDateTime before, Pageable pageable);

    /**
     * findShippingDelayed와 같은 조건의 건수.
     */
    long countShippingDelayed(LocalDateTime before);

    /**
     * 판매자별 배송 한 건. 상태 · 택배사는 enum 이름 문자열.
     *
     * @param shipped 발송됐는지(배송 중 · 배송 완료)
     * @param deliveredAt 배송 완료 전이면 null
     */
    record Delivery(
        Long deliveryId,
        Long orderId,
        Long sellerId,
        String status,
        String carrier,
        String trackingNumber,
        LocalDateTime shippedAt,
        LocalDateTime deliveredAt,
        boolean shipped) {

        /** 발송 전(배송 준비) 상태 이름 */
        public static final String READY = "READY";
    }

    /**
     * @param from 취소 전 상태 이름
     * @param delivery 취소 뒤 배송
     */
    record CancelledDelivery(String from, Delivery delivery) {
    }

    /**
     * 발송 전으로 남은 배송 한 건.
     *
     * @param createdAt 배송 생성 시각(멈춤 기준 시각)
     */
    record DelayedDelivery(Long orderId, Long deliveryId, LocalDateTime createdAt) {
    }

    /**
     * 배송을 만든 주체. 주문 상태 이력의 주체 종류 이름 · 회원 ID · 요청 · 작업 · 이벤트 이름.
     *
     * @param id 회원 ID. 시스템 · 이벤트는 null
     */
    record Actor(String type, Long id, String source) {
    }
}
