package com.bbcc.kidly.boundedcontext.product.out.outbox;

import com.bbcc.kidly.boundedcontext.product.domain.outbox.ProductOutboxMessage;
import com.bbcc.kidly.boundedcontext.product.domain.outbox.ProductOutboxRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 상품 아웃박스 메시지를 JPA로 저장 · 조회 · 삭제한다.
 */
public interface JpaProductOutboxRepository
    extends JpaRepository<ProductOutboxMessage, Long>, ProductOutboxRepository {

    List<ProductOutboxMessage> findBySentAtIsNullOrderByIdAsc(Limit limit);

    @Override
    default List<ProductOutboxMessage> findUnsent(int limit) {
        return findBySentAtIsNullOrderByIdAsc(Limit.of(limit));
    }

    // sent_at이 null인 행은 비교 결과가 참이 아니므로 지우지 않는다
    @Override
    @Modifying
    @Query("delete from ProductOutboxMessage m where m.sentAt < :cutoff")
    int deleteSentBefore(@Param("cutoff") LocalDateTime cutoff);
}
