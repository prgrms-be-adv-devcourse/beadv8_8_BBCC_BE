package com.bbcc.kidly.boundedcontext.payment.out;

import com.bbcc.kidly.boundedcontext.payment.domain.OrderCancellationRetry;
import com.bbcc.kidly.boundedcontext.payment.domain.OrderCancellationRetryRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaOrderCancellationRetryRepository
    extends JpaRepository<OrderCancellationRetry, Long>, OrderCancellationRetryRepository {
}
