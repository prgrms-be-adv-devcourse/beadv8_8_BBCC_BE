package com.bbcc.kidly.boundedcontext.payment.out;

import com.bbcc.kidly.boundedcontext.payment.domain.Charge;
import com.bbcc.kidly.boundedcontext.payment.domain.ChargeRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaChargeRepository extends JpaRepository<Charge, Long>, ChargeRepository {
}
