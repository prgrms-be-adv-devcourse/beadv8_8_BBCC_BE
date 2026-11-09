package com.bbcc.kidly.boundedcontext.review.app.port;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 커뮤니티 소유 DLT 보관과 처리 결과. 쓰기는 호출자의 트랜잭션에서 수행한다.
 */
public interface CommunityDeadLetters {

    boolean insert(Received message, LocalDateTime now);

    Page<Summary> findByState(State state, Pageable pageable);

    long countByState(State state);

    /**
     * 그 상태 가운데 가장 먼저 보관한 시각(운영 지표). 없으면 null.
     */
    LocalDateTime findOldestCreatedAt(State state);

    Optional<Summary> findSummary(Long id);

    Optional<Target> findForReplay(Long id);

    int markReprocessed(Long id, Long adminId, LocalDateTime now);

    int recordReplayFailure(Long id, Long adminId, LocalDateTime now, String failureType);

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
