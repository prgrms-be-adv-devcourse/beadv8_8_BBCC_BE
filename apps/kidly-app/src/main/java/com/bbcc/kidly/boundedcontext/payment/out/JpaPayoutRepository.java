package com.bbcc.kidly.boundedcontext.payment.out;

import com.bbcc.kidly.boundedcontext.payment.domain.Payout;
import com.bbcc.kidly.boundedcontext.payment.domain.PayoutRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaPayoutRepository extends JpaRepository<Payout, Long>, PayoutRepository {
}
