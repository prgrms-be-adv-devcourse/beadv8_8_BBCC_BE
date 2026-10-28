package com.bbcc.kidly.boundedcontext.payment.domain;

import java.util.List;
import java.util.Optional;

public interface OrderCancellationRetryRepository {

    OrderCancellationRetry save(OrderCancellationRetry retry);

    Optional<OrderCancellationRetry> findById(Long id);

    Optional<OrderCancellationRetry> findByOrderId(Long orderId);

    List<OrderCancellationRetry> findAllByOrderByIdAsc();

    void delete(OrderCancellationRetry retry);
}
