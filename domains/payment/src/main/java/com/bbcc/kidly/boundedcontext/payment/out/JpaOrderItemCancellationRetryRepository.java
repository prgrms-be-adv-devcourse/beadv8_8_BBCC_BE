package com.bbcc.kidly.boundedcontext.payment.out;

import com.bbcc.kidly.boundedcontext.payment.domain.OrderItemCancellationRetry;
import com.bbcc.kidly.boundedcontext.payment.domain.OrderItemCancellationRetryRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface JpaOrderItemCancellationRetryRepository
    extends JpaRepository<OrderItemCancellationRetry, Long>, OrderItemCancellationRetryRepository {

    @Override
    @EntityGraph(attributePaths = "items")
    @Query("select r from OrderItemCancellationRetry r where r.id = :id")
    Optional<OrderItemCancellationRetry> findWithItemsById(Long id);

    @Override
    @Query("select min(r.createdAt) from OrderItemCancellationRetry r")
    Optional<LocalDateTime> findOldestCreatedAt();
}
