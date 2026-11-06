package com.bbcc.kidly.boundedcontext.payment.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderCancellationRetryRepository {

    OrderCancellationRetry save(OrderCancellationRetry retry);

    Optional<OrderCancellationRetry> findById(Long id);

    Optional<OrderCancellationRetry> findByOrderId(Long orderId);

    List<OrderCancellationRetry> findAllByOrderByIdAsc();

    long count();

    // 처음 실패해 기록된 시각. 기록이 없으면 비어 있다
    Optional<LocalDateTime> findOldestCreatedAt();

    void delete(OrderCancellationRetry retry);
}
