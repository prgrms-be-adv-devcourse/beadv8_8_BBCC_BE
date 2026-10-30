package com.bbcc.kidly.boundedcontext.order.out.persistence;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderOutboxRepository;
import com.bbcc.kidly.boundedcontext.order.domain.outbox.OrderOutboxMessage;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * 주문 이벤트 메시지 저장소 포트를 Spring Data JPA로 구현한다. 포트에 선언한 메서드만 노출한다.
 */
public interface JpaOrderOutboxRepository extends Repository<OrderOutboxMessage, Long>, OrderOutboxRepository {


    @Override
    @Modifying
    @Query("delete from OrderOutboxMessage m where m.sentAt < :cutoff")
    int deleteSentBefore(LocalDateTime cutoff);
}
