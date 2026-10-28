package com.bbcc.kidly.boundedcontext.payment.domain;

public interface CancelledOrderRepository {

    CancelledOrder save(CancelledOrder cancelledOrder);

    boolean existsByOrderId(Long orderId);
}
