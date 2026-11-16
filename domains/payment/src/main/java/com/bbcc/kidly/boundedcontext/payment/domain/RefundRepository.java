package com.bbcc.kidly.boundedcontext.payment.domain;

import java.util.Optional;

public interface RefundRepository {

    Refund save(Refund refund);

    Optional<Refund> findByCancellationId(Long cancellationId);
}
