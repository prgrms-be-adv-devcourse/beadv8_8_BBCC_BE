package com.bbcc.kidly.boundedcontext.product.out.outbox;

import com.bbcc.kidly.boundedcontext.product.domain.outbox.ProductOutboxMessage;
import com.bbcc.kidly.boundedcontext.product.domain.outbox.ProductOutboxRepository;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 상품 아웃박스 메시지를 JPA로 저장하고 조회한다.
 */
public interface JpaProductOutboxRepository
    extends JpaRepository<ProductOutboxMessage, Long>, ProductOutboxRepository {

    List<ProductOutboxMessage> findBySentAtIsNullOrderByIdAsc(Limit limit);

    @Override
    default List<ProductOutboxMessage> findUnsent(int limit) {
        return findBySentAtIsNullOrderByIdAsc(Limit.of(limit));
    }
}
