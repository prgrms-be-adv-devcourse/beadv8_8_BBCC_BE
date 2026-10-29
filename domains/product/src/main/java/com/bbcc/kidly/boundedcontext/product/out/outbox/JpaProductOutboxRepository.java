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

/**
 * 상품 아웃박스 메시지를 JPA로 저장 · 조회 · 삭제한다.
 */
public interface JpaProductOutboxRepository
    extends JpaRepository<ProductOutboxMessage, Long>, ProductOutboxRepository {

    // JpaRepository와 ProductOutboxRepository의 save를 둘 다 물려받아, 이 타입으로 save를 부르면 컴파일러가 하나를 고르지 못한다.
    // 여기서 다시 선언해 하나로 정한다. 실행은 그대로 Spring Data의 기본 save가 맡는다.
    @Override
    ProductOutboxMessage save(ProductOutboxMessage message);

    // 같은 이벤트를 다시 발행했는지 JpaProductEventOutbox가 확인한다. app은 쓰지 않으므로 domain 요청서에 두지 않는다
    boolean existsByEventId(UUID eventId);

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
