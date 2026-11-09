package com.bbcc.kidly.boundedcontext.notification.app.port;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 알림 소유 DLT 보관과 처리 결과. 쓰기는 호출자의 트랜잭션에서 수행한다.
 */
public interface NotificationDeadLetters {

    boolean insert(Received message, LocalDateTime now);

    Page<Summary> findByState(State state, Pageable pageable);

    long countByState(State state);

    Optional<Summary> findSummary(Long id);

    Optional<Target> findForReplay(Long id);

    int markReprocessed(Long id, Long adminId, LocalDateTime now);

    int recordReplayFailure(Long id, Long adminId, LocalDateTime now, String failureType);

    // 원래 토픽 · 키가 같은 기록의 본문을 비운다(탈퇴 회원 개인정보 파기). 행은 남긴다
    int erasePayloadOf(String sourceTopic, String messageKey, LocalDateTime now);

    enum State {
        OPEN, REPROCESSED
    }

    record Received(String dltTopic, int dltPartition, long dltOffset, String sourceTopic, Integer sourcePartition,
        Long sourceOffset, String messageKey, String eventType, String eventId, String payload, String failureType) {
    }

    record Message(String sourceTopic, String messageKey, String eventType, String eventId, String payload) {
    }

    record Target(State state, Message message) {
    }

    interface Summary {
        Long getId();

        String getDltTopic();

        int getDltPartition();

        long getDltOffset();

        String getSourceTopic();

        Integer getSourcePartition();

        Long getSourceOffset();

        String getMessageKey();

        String getEventType();

        String getEventId();

        String getFailureType();

        State getState();

        int getReplayCount();

        Long getLastReplayedBy();

        LocalDateTime getLastReplayedAt();

        String getLastReplayFailureType();

        LocalDateTime getCreatedAt();
    }
}
