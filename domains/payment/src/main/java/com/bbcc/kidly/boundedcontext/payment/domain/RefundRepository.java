package com.bbcc.kidly.boundedcontext.payment.domain;

public interface RefundRepository {

    Refund save(Refund refund);

    boolean existsByCancellationId(Long cancellationId);
}
