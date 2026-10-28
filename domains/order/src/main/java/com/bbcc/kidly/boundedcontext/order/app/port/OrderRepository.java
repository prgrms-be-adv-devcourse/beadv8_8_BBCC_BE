package com.bbcc.kidly.boundedcontext.order.app.port;

import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
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
     * 바뀐 주문을 지금 DB에 반영한다(#449). 받은 이벤트 처리 실패를 리스너 안에서 잡아 보관하려고 쓴다.
     */
    void flush();

    boolean existsById(Long id);

    /**
     * 회원의 주문 1건. 남의 주문이면 없는 것으로 본다.
     */
    Optional<Order> findByIdAndMemberId(Long id, Long memberId);

    /**
     * 회원이 같은 요청 키로 만든 주문(#351). 키는 회원마다 하나의 주문에만 쓰인다(유니크 제약).
     */
    Optional<Order> findByMemberIdAndIdempotencyKey(Long memberId, String idempotencyKey);

    /**
     * 회원의 주문 1건을 주문상품과 함께 한 번에 읽는다(타임라인, #352). 남의 주문이면 없는 것으로 본다. 주문상품은 ID 오름차순.
     */
    Optional<Order> findWithItemsByIdAndMemberId(Long id, Long memberId);

    /**
     * ID 목록의 주문을 주문상품과 함께 한 번에 읽는다(목록 화면의 건마다 조회 방지). 순서는 보장하지 않으므로 ID로 찾는다.
     * 주문상품은 ID 오름차순. 없는 ID는 결과에서 빠진다.
     */
    List<Order> findAllWithItemsByIdIn(Collection<Long> ids);

    /**
     * 회원의 주문 목록. 최근 주문부터.
     */
    Page<Order> findAllByMemberIdOrderByIdDesc(Long memberId, Pageable pageable);

    /**
     * 회원의 주문 수. 목록을 읽을 수 없는 큰 page의 빈 페이지에 전체 건수를 채운다.
     */
    long countByMemberId(Long memberId);

    /**
     * 모든 회원의 주문 목록(관리자). 최근 주문부터.
     */
    Page<Order> findAllByOrderByIdDesc(Pageable pageable);

    /**
     * 전체 주문 수. 목록을 읽을 수 없는 큰 page의 빈 페이지에 전체 건수를 채운다.
     */
    long count();

    /**
     * 결제 마감이 지난(now 포함) 주문 ID. afterId보다 큰 ID만 주문 ID 오름차순으로 limit건(한 실행 안의 ID 커서).
     */
    List<Long> findOverduePaymentOrderIds(Order.Status status, LocalDateTime now, Long afterId, Limit limit);

    /**
     * 자동 구매확정 대상을 좁힌다: 주문상품 상태가 status이고 deliveredBefore 이전(포함)에 배송 완료된 것.
     * afterId보다 큰 주문상품 ID만 오름차순으로 limit건(ID 커서). 확정해도 되는지 최종 판단은 주문이 한다.
     */
    List<AutoConfirmRow> findAutoConfirmTargets(
        OrderItem.Status status, LocalDateTime deliveredBefore, Long afterId, Limit limit);
}
