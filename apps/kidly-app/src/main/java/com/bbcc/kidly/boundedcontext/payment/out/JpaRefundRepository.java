package com.bbcc.kidly.boundedcontext.payment.out;

import com.bbcc.kidly.boundedcontext.payment.domain.Refund;
import com.bbcc.kidly.boundedcontext.payment.domain.RefundRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaRefundRepository extends JpaRepository<Refund, Long>, RefundRepository {
}
