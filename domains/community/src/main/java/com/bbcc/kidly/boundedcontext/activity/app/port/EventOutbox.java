package com.bbcc.kidly.boundedcontext.activity.app.port;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 커뮤니티 이벤트 아웃박스(표 community.event_outbox). 넣기는 부르는 쪽 트랜잭션에서, 릴레이의 읽기 · 삭제 · 실패 기록은 한 건씩 한다.
 * 구현은 out/store.
 */
public interface EventOutbox {

    /**
     * 보낼 메시지를 넣는다(여러 건이면 JDBC batch 한 번).
     */
    void append(List<NewMessage> messages);

    /**
     * 격리되지 않은 행을 id 순으로 limit만큼(다음 시각과 상관없이. 순서를 지키려고 릴레이가 앞 행에서 멈춘다).
     */
    List<PendingMessage> findPending(int limit);

    /**
     * 보낸 행을 지운다. 읽은 행과 같은 행(id와 eventId가 모두 같음)일 때만 지운다(id를 다시 쓰는 DB에서도 다른 행을 지우지 않게).
     */
    void delete(long id, UUID eventId);

    /**
     * 실패를 기록한다: 시도 +1 · 다음 시각 · 실패 종류(id와 eventId가 모두 같은 행만).
     */
    void recordFailure(long id, UUID eventId, LocalDateTime nextAttemptAt, String failureType);

    /**
     * 시도 상한에 닿아 격리한다(시도 +1 · 격리 시각 · 실패 종류, id와 eventId가 모두 같은 행만). 격리 행은 지우지 않고 남긴다.
     */
    void quarantine(long id, UUID eventId, LocalDateTime quarantinedAt, String failureType);

    long countPending();

    long countQuarantined();

    /**
     * 가장 오래된 대기 행의 넣은 시각. 없으면 null.
     */
    LocalDateTime findOldestPendingCreatedAt();

    /**
     * @param key Kafka 키(이벤트의 correlationId)
     * @param payload 보낼 본문 JSON(공통 칸 7개 + data)
     */
    record NewMessage(UUID eventId, String eventType, String key, String payload, LocalDateTime createdAt) {
    }

    record PendingMessage(long id, UUID eventId, String eventType, String key, String payload, int attempts,
                          LocalDateTime nextAttemptAt) {
    }
}
