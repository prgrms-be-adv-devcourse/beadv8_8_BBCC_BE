package com.bbcc.kidly.boundedcontext.order.app.port;

import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 주문 저장소. 구현은 out/persistence (Spring Data가 만든다).
 */
public interface OrderRepository {

    /**
     * 자동 구매확정 대상(주문 ID, 주문상품 ID).
     */
    interface AutoConfirmRow {

        Long getOrderId();

        Long getOrderItemId();
    }

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

    /**
     * 결제 마감이 지난 주문(상태 · 마감 시각 조건). 주문 ID 오름차순 100건.
     */
    List<Order> findTop100ByStatusAndPaymentDueAtLessThanEqualOrderByIdAsc(Order.Status status, LocalDateTime now);

    /**
     * 자동 구매확정 대상을 좁힌다: 주문상품 상태가 status이고 deliveredBefore 이전(포함)에 배송 완료된 것.
     * 주문상품 ID 오름차순, 건수는 pageable로. 확정해도 되는지 최종 판단은 주문이 한다.
     */
    List<AutoConfirmRow> findAutoConfirmTargets(
        OrderItem.Status status, LocalDateTime deliveredBefore, Pageable pageable);
}
