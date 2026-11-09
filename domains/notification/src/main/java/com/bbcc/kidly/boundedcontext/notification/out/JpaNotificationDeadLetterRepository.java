package com.bbcc.kidly.boundedcontext.notification.out;

import com.bbcc.kidly.boundedcontext.notification.app.port.NotificationDeadLetters.State;
import com.bbcc.kidly.boundedcontext.notification.app.port.NotificationDeadLetters.Summary;
import com.bbcc.kidly.boundedcontext.notification.out.deadletter.NotificationDeadLetterRecord;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface JpaNotificationDeadLetterRepository extends Repository<NotificationDeadLetterRecord, Long> {

    String SUMMARY = """
        select m.id as id, m.dltTopic as dltTopic, m.dltPartition as dltPartition, m.dltOffset as dltOffset,
               m.sourceTopic as sourceTopic, m.sourcePartition as sourcePartition, m.sourceOffset as sourceOffset,
               m.messageKey as messageKey, m.eventType as eventType, m.eventId as eventId, m.failureType as failureType,
               m.state as state, m.replayCount as replayCount, m.lastReplayedBy as lastReplayedBy,
               m.lastReplayedAt as lastReplayedAt, m.lastReplayFailureType as lastReplayFailureType,
               m.createdAt as createdAt
        from NotificationDeadLetterRecord m
        """;

    Optional<NotificationDeadLetterRecord> findById(Long id);

    @Query(value = SUMMARY + "where m.state = :state order by m.id desc",
        countQuery = "select count(m) from NotificationDeadLetterRecord m where m.state = :state")
    Page<Summary> findSummaries(State state, Pageable pageable);

    @Query(SUMMARY + "where m.id = :id")
    Optional<Summary> findSummary(Long id);

    long countByState(State state);
}
