package com.bbcc.kidly.boundedcontext.payment.out;

import com.bbcc.kidly.boundedcontext.payment.domain.DevCharge;
import com.bbcc.kidly.boundedcontext.payment.domain.DevChargeRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaDevChargeRepository extends JpaRepository<DevCharge, Long>, DevChargeRepository {
}
