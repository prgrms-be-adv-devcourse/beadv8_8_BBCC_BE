package com.bbcc.kidly.boundedcontext.order.app.port;

import com.bbcc.kidly.boundedcontext.order.domain.recovery.OrderEventFailure;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 받은 이벤트 처리 실패 기록 저장소(#449). 구현은 out/persistence (Spring Data가 만든다).
 */
public interface OrderEventFailureRepository {

    OrderEventFailure save(OrderEventFailure failure);

    Optional<OrderEventFailure> findById(Long id);

    /**
     * 기록을 행 쓰기 잠금으로 읽는다. 재처리 · 종료가 같은 기록을 한 번에 하나만 처리하게 해, 늦은 쪽은 앞쪽이 끝날 때까지
     * 기다렸다가 최신 기록을 본다.
     */
    Optional<OrderEventFailure> findByIdForUpdate(Long id);

    /**
     * 주문 · 이벤트 종류의 기록. (주문, 종류)당 1건(유니크).
     */
    Optional<OrderEventFailure> findByOrderIdAndEventType(Long orderId, OrderEventFailure.EventType eventType);

    /**
     * 주문 · 이벤트 종류의 기록을 기다리지 않는 행 쓰기 잠금으로 읽는다. 재처리 · 종료가 그 기록을 잠그고 있으면 바로 실패한다
     * (리스너의 실패 보관이 주문 행을 쥔 채 기록 잠금을 기다리며 멈추지 않게).
     */
    Optional<OrderEventFailure> findByOrderIdAndEventTypeForUpdateNoWait(Long orderId,
        OrderEventFailure.EventType eventType);

    /**
     * 주문의 상태별 기록을 행 쓰기 잠금으로 읽되, 다른 트랜잭션(재처리 · 종료)이 잡고 있는 기록은 기다리지 않고 건너뛴다(#598).
     */
    List<OrderEventFailure> findByOrderIdAndStatusForUpdateSkipLocked(Long orderId, OrderEventFailure.Status status);

    /**
     * 모든 기록. 최근 기록부터.
     */
    Page<OrderEventFailure> findAllByOrderByIdDesc(Pageable pageable);

    /**
     * 상태별 기록. 최근 기록부터.
     */
    Page<OrderEventFailure> findAllByStatusOrderByIdDesc(OrderEventFailure.Status status, Pageable pageable);

    /**
     * 전체 기록 수. 목록을 읽을 수 없는 큰 page의 빈 페이지에 전체 건수를 채운다.
     */
    long count();

    /**
     * 상태별 기록 수. 목록을 읽을 수 없는 큰 page의 빈 페이지에 전체 건수를 채운다.
     */
    long countByStatus(OrderEventFailure.Status status);
}
