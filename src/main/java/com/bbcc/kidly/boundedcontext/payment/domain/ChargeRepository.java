package com.bbcc.kidly.boundedcontext.payment.domain;

import java.util.Optional;

public interface ChargeRepository {

    Charge save(Charge charge);

    Optional<Charge> findById(Long id);

    Optional<Charge> findByTossOrderId(String tossOrderId);
}
