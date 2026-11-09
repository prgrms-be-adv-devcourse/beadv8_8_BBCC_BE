package com.bbcc.kidly.boundedcontext.order.app.port;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Kafka로 보낼 주문 이벤트 메시지 저장소(#540). 구현은 out/outbox(OrderOutboxStore). 메시지(아웃박스 엔티티)는 전달 장치라 out에 있고,
 * app은 이 포트의 값({@link OutboxMessage})으로만 다룬다(#642). 부르는 쪽 트랜잭션에 합류한다.
 */
public interface OrderOutboxRepository {

    /**
     * 격리되지 않았고, 같은 토픽 · 키의 앞선 격리 메시지가 없는 미전송 메시지를 id 순서로 limit건.
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

    OutboxMessage quarantine(Long id, String error, LocalDateTime now);

    Page<OutboxStatus> findQuarantined(Pageable pageable);

    long countQuarantined();

    /**
     * 보내지 않았고 격리되지 않은 메시지 수(운영 지표, #1067).
     */
    long countUnsent();

    /**
     * 보내지 않았고 격리되지 않은 메시지 중 가장 앞(id 순서) 메시지의 만든 시각(운영 지표, #1067). 없으면 빈 값.
     */
    Optional<LocalDateTime> findOldestUnsentCreatedAt();

    Optional<OutboxStatus> findStatus(Long id);

    /**
     * 관리자 재시도 간 경쟁을 막는 행 잠금. 호출한 트랜잭션이 끝날 때까지 유지한다.
     */
    boolean lockById(Long id);

    void retryQuarantined(Long id, Long adminId, LocalDateTime now);

    /**
     * 본문을 읽지 않는 관리자용 메타데이터. 목록에서도 큰 본문을 메모리에 올리지 않는다.
     */
    interface OutboxStatus {
        Long getId();

        UUID getEventId();

        String getEventType();

        String getTopic();

        String getMessageKey();

        int getAttemptCount();

        String getLastError();

        LocalDateTime getCreatedAt();

        LocalDateTime getQuarantinedAt();

        LocalDateTime getSentAt();

        LocalDateTime getLastRetriedAt();

        Long getLastRetriedBy();

        int getRetryCount();
    }

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
