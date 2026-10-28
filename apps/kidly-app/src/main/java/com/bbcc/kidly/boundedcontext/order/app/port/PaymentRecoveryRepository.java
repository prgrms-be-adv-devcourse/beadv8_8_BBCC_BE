package com.bbcc.kidly.boundedcontext.order.app.port;

import com.bbcc.kidly.boundedcontext.order.domain.recovery.PaymentRecovery;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 결제 완료 반영 실패 기록 저장소(#353). 구현은 out/persistence (Spring Data가 만든다).
 */
public interface PaymentRecoveryRepository {

    PaymentRecovery save(PaymentRecovery recovery);

    Optional<PaymentRecovery> findById(Long id);

    /**
     * 기록을 행 쓰기 잠금으로 읽는다. 재처리 · 취소가 같은 기록을 한 번에 하나만 처리하게 해, 늦은 쪽은 앞쪽이 끝날 때까지
     * 기다렸다가 최신 기록을 본다.
     */
    Optional<PaymentRecovery> findByIdForUpdate(Long id);

    /**
     * 주문의 기록. 주문당 1건(order_id 유니크).
     */
    Optional<PaymentRecovery> findByOrderId(Long orderId);

    /**
     * 주문의 기록을 기다리지 않는 행 쓰기 잠금으로 읽는다. 재처리 · 취소가 그 기록을 잠그고 있으면 바로 실패한다
     * (결제 완료 리스너의 실패 보관이 주문 행을 쥔 채 기록 잠금을 기다리며 멈추지 않게).
     */
    Optional<PaymentRecovery> findByOrderIdForUpdateNoWait(Long orderId);

    /**
     * 모든 기록. 최근 기록부터.
     */
    Page<PaymentRecovery> findAllByOrderByIdDesc(Pageable pageable);

    /**
     * 상태별 기록. 최근 기록부터.
     */
    Page<PaymentRecovery> findAllByStatusOrderByIdDesc(PaymentRecovery.Status status, Pageable pageable);

    /**
     * 전체 기록 수. 목록을 읽을 수 없는 큰 page의 빈 페이지에 전체 건수를 채운다.
     */
    long count();

    /**
     * 상태별 기록 수. 목록을 읽을 수 없는 큰 page의 빈 페이지에 전체 건수를 채운다.
     */
    long countByStatus(PaymentRecovery.Status status);
}
