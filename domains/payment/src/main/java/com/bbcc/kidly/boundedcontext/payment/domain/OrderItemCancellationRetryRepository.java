package com.bbcc.kidly.boundedcontext.payment.domain;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderItemCancellationRetryRepository {

    OrderItemCancellationRetry save(OrderItemCancellationRetry retry);

    Optional<OrderItemCancellationRetry> findById(Long id);

    // 트랜잭션 밖에서 읽어도 품목을 쓸 수 있게 함께 읽는다
    Optional<OrderItemCancellationRetry> findWithItemsById(Long id);

    Optional<OrderItemCancellationRetry> findByCancellationId(Long cancellationId);

    // 처음 실패한 순서
    Page<OrderItemCancellationRetry> findAllByOrderByIdAsc(Pageable pageable);

    long count();

    // 처음 실패해 기록된 시각. 기록이 없으면 비어 있다
    Optional<LocalDateTime> findOldestCreatedAt();

    void delete(OrderItemCancellationRetry retry);
}
