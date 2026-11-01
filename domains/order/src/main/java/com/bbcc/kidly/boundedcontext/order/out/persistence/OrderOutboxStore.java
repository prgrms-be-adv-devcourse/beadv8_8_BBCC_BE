package com.bbcc.kidly.boundedcontext.order.out.persistence;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderOutboxRepository;
import com.bbcc.kidly.boundedcontext.order.out.messaging.OrderOutboxMessage;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

/**
 * 아웃박스 메시지 저장소 포트 구현(#642). 보냄 · 실패 기록은 같은 트랜잭션에서 읽어 둔 엔티티를 id로 다시 찾아(영속성 컨텍스트, 조회 없음)
 * 바꾸고, 커밋 때 함께 반영된다.
 */
@Component
@RequiredArgsConstructor
public class OrderOutboxStore implements OrderOutboxRepository {

    private final JpaOrderOutboxRepository jpaRepository;

    @Override
    public List<OutboxMessage> findUnsent(Limit limit) {
        return jpaRepository.findBySentAtIsNullOrderByIdAsc(limit).stream().map(OrderOutboxStore::toMessage).toList();
    }

    @Override
    public void markSent(Long id, LocalDateTime now) {
        find(id).markSent(now);
    }

    @Override
    public OutboxMessage recordFailure(Long id, String error) {
        OrderOutboxMessage message = find(id);
        message.recordFailure(error);
        return toMessage(message);
    }

    @Override
    public int deleteSentBefore(LocalDateTime cutoff) {
        return jpaRepository.deleteSentBefore(cutoff);
    }

    private OrderOutboxMessage find(Long id) {
        return jpaRepository.findById(id)
            .orElseThrow(() -> new IllegalStateException("아웃박스 메시지가 없습니다: id=" + id));
    }

    private static OutboxMessage toMessage(OrderOutboxMessage message) {
        return new OutboxMessage(message.getId(), message.getEventId(), message.getEventType(), message.getTopic(),
            message.getMessageKey(), message.getPayload(), message.getAttemptCount(), message.getLastError());
    }
}
