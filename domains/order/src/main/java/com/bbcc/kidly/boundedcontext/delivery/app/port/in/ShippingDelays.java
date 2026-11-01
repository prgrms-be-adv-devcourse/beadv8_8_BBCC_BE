package com.bbcc.kidly.boundedcontext.delivery.app.port.in;

import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 발송 전(READY)으로 남은 배송 조회(#617-2 · #641). 관리자 멈춘 주문(발송 지연)이 읽는다. 구현은 배송 app이고 부르는 쪽 트랜잭션에 합류한다.
 */
public interface ShippingDelays {

    /**
     * 발송 전(READY)이고 생성 시각 &lt; before인 배송. 생성 시각 · 배송 ID 오름차순. 목록 1번 + 건수 1번으로 읽는다.
     */
    Page<ShippingDelayedView> findShippingDelayed(LocalDateTime before, Pageable pageable);

    /**
     * findShippingDelayed와 같은 조건의 건수.
     */
    long countShippingDelayed(LocalDateTime before);

    /**
     * @param createdAt 배송 생성 시각(멈춤 기준 시각)
     */
    record ShippingDelayedView(Long orderId, Long deliveryId, LocalDateTime createdAt) {
    }
}
