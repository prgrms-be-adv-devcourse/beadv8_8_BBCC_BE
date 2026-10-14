package com.bbcc.kidly.boundedcontext.order.app.port;

import com.bbcc.kidly.boundedcontext.order.domain.delivery.Delivery;
import java.util.List;
import java.util.Optional;
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
}
