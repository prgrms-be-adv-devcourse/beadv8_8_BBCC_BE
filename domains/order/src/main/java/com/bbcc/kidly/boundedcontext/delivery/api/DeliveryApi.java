package com.bbcc.kidly.boundedcontext.delivery.api;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 배송이 다른 컨텍스트(주문)에 내주는 기능(#617). 구현은 배송 app이고 부르는 쪽 트랜잭션에 합류한다.
 */
public interface DeliveryApi {

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
     * 발송 전(READY)이고 생성 시각 &lt; before인 배송(#617-2). 생성 시각 · 배송 ID 오름차순. 목록 1번 + 건수 1번으로 읽는다.
     */
    Page<ShippingDelayedView> findShippingDelayed(LocalDateTime before, Pageable pageable);

    /**
     * findShippingDelayed와 같은 조건의 건수.
     */
    long countShippingDelayed(LocalDateTime before);
}
