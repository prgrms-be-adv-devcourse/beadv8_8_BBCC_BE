package com.bbcc.kidly.boundedcontext.payment.domain;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PaymentRepository {

    Payment save(Payment payment);

    boolean existsByOrderId(Long orderId);

    Optional<Payment> findById(Long id);

    Page<Payment> findAllByMemberIdOrderByIdDesc(Long memberId, Pageable pageable);
}
