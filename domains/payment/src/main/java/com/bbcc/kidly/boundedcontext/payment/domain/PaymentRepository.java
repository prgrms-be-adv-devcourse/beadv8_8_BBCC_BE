package com.bbcc.kidly.boundedcontext.payment.domain;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PaymentRepository {

    Payment save(Payment payment);

    boolean existsByOrderId(Long orderId);

    Optional<Payment> findById(Long id);

    Optional<Payment> findByOrderId(Long orderId);

    Page<Payment> findAllByMemberIdOrderByIdDesc(Long memberId, Pageable pageable);

    Page<Payment> findAllByMemberIdAndOrderIdOrderByIdDesc(Long memberId, Long orderId, Pageable pageable);

    Page<Payment> findAllByOrderByIdDesc(Pageable pageable);

    /**
     * [from, to) 사이에 결제된 금액 · 건수를 결제일별로 준다. 결제가 없는 날은 빠진다.
     */
    List<DailyPaymentTotal> sumDailyPaidBetween(LocalDateTime from, LocalDateTime to);

    long countByStatusIn(Collection<PaymentStatus> statuses);
}
