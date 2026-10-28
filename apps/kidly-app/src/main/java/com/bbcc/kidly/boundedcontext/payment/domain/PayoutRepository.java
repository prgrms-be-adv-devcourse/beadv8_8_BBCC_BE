package com.bbcc.kidly.boundedcontext.payment.domain;

import java.util.Optional;

public interface PayoutRepository {

    Payout save(Payout payout);

    Optional<Payout> findBySettlementId(Long settlementId);
}
