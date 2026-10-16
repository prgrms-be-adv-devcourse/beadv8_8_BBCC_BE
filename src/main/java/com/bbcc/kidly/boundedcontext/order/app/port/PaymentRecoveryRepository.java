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
     * 주문의 기록. 주문당 1건(order_id 유니크).
     */
    Optional<PaymentRecovery> findByOrderId(Long orderId);

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
