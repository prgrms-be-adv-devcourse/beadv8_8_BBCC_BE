package com.bbcc.kidly.boundedcontext.order.out.persistence;

import com.bbcc.kidly.boundedcontext.order.out.outbox.OrderOutboxMessage;
import com.bbcc.kidly.boundedcontext.order.out.outbox.OrderOutboxStore;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * 아웃박스 메시지 Spring Data JPA 저장소. app은 {@link OrderOutboxStore}(포트 구현)로만 쓰고, 저장은 아웃박스 쓰기(out)가 한다.
 */
public interface JpaOrderOutboxRepository extends Repository<OrderOutboxMessage, Long> {

    OrderOutboxMessage save(OrderOutboxMessage message);

    Optional<OrderOutboxMessage> findById(Long id);

    List<OrderOutboxMessage> findBySentAtIsNullOrderByIdAsc(Limit limit);

    @Modifying
    @Query("delete from OrderOutboxMessage m where m.sentAt < :cutoff")
    int deleteSentBefore(LocalDateTime cutoff);
}
