package com.bbcc.kidly.boundedcontext.payment.out;

import com.bbcc.kidly.boundedcontext.payment.domain.CancelledOrder;
import com.bbcc.kidly.boundedcontext.payment.domain.CancelledOrderRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaCancelledOrderRepository extends JpaRepository<CancelledOrder, Long>, CancelledOrderRepository {
}
