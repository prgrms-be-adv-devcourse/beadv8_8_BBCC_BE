package com.bbcc.kidly.boundedcontext.payment.domain;

public interface PaymentRepository {

    Payment save(Payment payment);

    boolean existsByOrderId(Long orderId);
}
