package com.bbcc.kidly.boundedcontext.order.app.port;

import com.bbcc.kidly.boundedcontext.order.domain.outbox.OrderOutboxMessage;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;

/**
 * Kafka로 보낼 주문 이벤트 메시지 저장소(#540). 구현은 out/persistence (Spring Data가 만든다).
 */
public interface OrderOutboxRepository {

    OrderOutboxMessage save(OrderOutboxMessage message);

    /**
     * 릴레이 잠금을 기다리지 않고 얻는다. 얻으면 true, 다른 트랜잭션이 쥐고 있으면 바로 false.
     * 잠금은 지금 트랜잭션이 끝날 때 풀리므로 트랜잭션 안에서 부르고, 보내기 · 기록을 같은 트랜잭션에서 한다
     * (서버가 여러 대여도 한 번에 하나의 릴레이만 돌아 순서가 지켜진다).
     */
    boolean tryLockRelay();

    /**
     * 아직 보내지 않은 메시지를 id 순서로 limit건.
     */
    List<OrderOutboxMessage> findBySentAtIsNullOrderByIdAsc(Limit limit);

    /**
     * 보낸 시각이 cutoff보다 이른 메시지를 지운다. 안 보낸 메시지는 지우지 않는다.
     *
     * @return 지운 건수
     */
    int deleteSentBefore(LocalDateTime cutoff);
}
