package com.bbcc.kidly.boundedcontext.order.app.port;

import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 주문 저장소. 구현은 out/persistence (Spring Data가 만든다).
 */
public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(Long id);

    /**
     * 회원의 주문 1건. 남의 주문이면 없는 것으로 본다.
     */
    Optional<Order> findByIdAndMemberId(Long id, Long memberId);

    /**
     * 회원의 주문 목록. 최근 주문부터.
     */
    Page<Order> findAllByMemberIdOrderByIdDesc(Long memberId, Pageable pageable);
}
