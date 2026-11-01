package com.bbcc.kidly.boundedcontext.order.app.port;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;

/**
 * Kafka로 보낼 주문 이벤트 메시지 저장소(#540). 구현은 out/outbox(OrderOutboxStore). 메시지(아웃박스 엔티티)는 전달 장치라 out에 있고,
 * app은 이 포트의 값({@link OutboxMessage})으로만 다룬다(#642). 부르는 쪽 트랜잭션에 합류한다.
 */
public interface OrderOutboxRepository {

    /**
     * 아직 보내지 않은 메시지를 id 순서로 limit건.
     */
    List<OutboxMessage> findUnsent(Limit limit);

    /**
     * 보냈다. 시도를 1 늘리고 보낸 시각을 기록한다. 이미 보낸 메시지는 바꾸지 않는다.
     */
    void markSent(Long id, LocalDateTime now);

    /**
     * 보내지 못했다. 시도를 1 늘리고 마지막 오류를 바꾼다(칸 길이를 넘으면 앞부분만). 본문은 오류에 넣지 않는다(호출하는 쪽 책임).
     *
     * @return 기록한 뒤 메시지(시도 · 마지막 오류)
     */
    OutboxMessage recordFailure(Long id, String error);

    /**
     * 보낸 시각이 cutoff보다 이른 메시지를 지운다. 안 보낸 메시지는 지우지 않는다.
     *
     * @return 지운 건수
     */
    int deleteSentBefore(LocalDateTime cutoff);

    /**
     * 보낼 메시지 한 건.
     *
     * @param eventType 헤더 eventType에 넣을 값(예: order.created)
     * @param messageKey Kafka 메시지 키(이벤트의 correlationId)
     * @param payload 보낼 본문 JSON
     * @param lastError 마지막으로 보내지 못한 이유. 없으면 null
     */
    record OutboxMessage(
        Long id,
        UUID eventId,
        String eventType,
        String topic,
        String messageKey,
        String payload,
        int attemptCount,
        String lastError) {
    }
}
