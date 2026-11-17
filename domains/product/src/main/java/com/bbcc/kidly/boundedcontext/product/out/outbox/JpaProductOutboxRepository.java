package com.bbcc.kidly.boundedcontext.product.out.outbox;

import com.bbcc.kidly.boundedcontext.product.domain.outbox.ProductOutboxMessage;
import com.bbcc.kidly.boundedcontext.product.domain.outbox.ProductOutboxRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaProductOutboxRepository
    extends JpaRepository<ProductOutboxMessage, Long>, ProductOutboxRepository {

    @Override
    ProductOutboxMessage save(ProductOutboxMessage message);

    boolean existsByEventId(UUID eventId);

    List<ProductOutboxMessage> findBySentAtIsNullOrderByIdAsc(Limit limit);

    @Override
    default List<ProductOutboxMessage> findUnsent(int limit) {
        return findBySentAtIsNullOrderByIdAsc(Limit.of(limit));
    }

    @Override
    @Modifying
    @Query("delete from ProductOutboxMessage m where m.sentAt < :cutoff")
    int deleteSentBefore(@Param("cutoff") LocalDateTime cutoff);
}
