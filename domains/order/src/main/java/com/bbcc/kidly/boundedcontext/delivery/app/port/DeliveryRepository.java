package com.bbcc.kidly.boundedcontext.delivery.app.port;

import com.bbcc.kidly.boundedcontext.delivery.domain.Delivery;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 판매자별 주문(배송) 저장소. 구현은 out/persistence (Spring Data가 만든다).
 */
public interface DeliveryRepository {

    Delivery save(Delivery delivery);

    /**
     * 판매자 본인의 배송 1건. 남의 배송이면 없는 것으로 본다.
     */
    Optional<Delivery> findByIdAndSellerId(Long id, Long sellerId);

    /**
     * 주문의 배송들. 만든 순서대로.
     */
    List<Delivery> findAllByOrderIdOrderByIdAsc(Long orderId);

    /**
     * 판매자의 배송 목록. 최근 것부터.
     */
    Page<Delivery> findAllBySellerIdOrderByIdDesc(Long sellerId, Pageable pageable);

    /**
     * 판매자의 배송 수. 목록을 읽을 수 없는 큰 page의 빈 페이지에 전체 건수를 채운다.
     */
    long countBySellerId(Long sellerId);

    /**
     * 발송 지연 목록 한 줄(#617-2). 지연 로딩 없이 필요한 값만 읽는다.
     */
    interface ShippingDelayedRow {

        Long getOrderId();

        Long getDeliveryId();

        LocalDateTime getCreatedAt();
    }

    /**
     * 발송 전(READY)이고 생성 시각 &lt; before인 배송. 생성 시각 · 배송 ID 오름차순.
     */
    Page<ShippingDelayedRow> findShippingDelayed(LocalDateTime before, Pageable pageable);

    long countShippingDelayed(LocalDateTime before);

    Optional<Delivery> findById(Long id);

    /**
     * 택배 조회 대상(#650): 상태가 status이고 조회를 멈추지 않았고 운송장이 있는 배송. afterId보다 큰 ID만 오름차순으로 limit건(ID 커서).
     */
    List<Delivery> findTrackingTargets(Delivery.Status status, Long afterId, Limit limit);
}
