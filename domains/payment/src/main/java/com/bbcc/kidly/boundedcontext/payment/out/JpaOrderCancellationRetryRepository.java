package com.bbcc.kidly.boundedcontext.payment.out;

import com.bbcc.kidly.boundedcontext.payment.domain.OrderCancellationRetry;
import com.bbcc.kidly.boundedcontext.payment.domain.OrderCancellationRetryRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface JpaOrderCancellationRetryRepository
    extends JpaRepository<OrderCancellationRetry, Long>, OrderCancellationRetryRepository {

    @Override
    @Query("select min(r.createdAt) from OrderCancellationRetry r")
    Optional<LocalDateTime> findOldestCreatedAt();
}
