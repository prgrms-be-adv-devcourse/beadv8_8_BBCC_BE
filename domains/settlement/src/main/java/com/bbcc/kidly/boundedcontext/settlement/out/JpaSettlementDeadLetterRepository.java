package com.bbcc.kidly.boundedcontext.settlement.out;

import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementDeadLetters.State;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementDeadLetters.Summary;
import com.bbcc.kidly.boundedcontext.settlement.out.deadletter.SettlementDeadLetterRecord;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface JpaSettlementDeadLetterRepository extends Repository<SettlementDeadLetterRecord, Long> {

    String SUMMARY = """
        select m.id as id, m.dltTopic as dltTopic, m.dltPartition as dltPartition, m.dltOffset as dltOffset,
               m.sourceTopic as sourceTopic, m.sourcePartition as sourcePartition, m.sourceOffset as sourceOffset,
               m.messageKey as messageKey, m.eventType as eventType, m.eventId as eventId, m.failureType as failureType,
               m.state as state, m.replayCount as replayCount, m.lastReplayedBy as lastReplayedBy,
               m.lastReplayedAt as lastReplayedAt, m.lastReplayFailureType as lastReplayFailureType,
               m.createdAt as createdAt
        from SettlementDeadLetterRecord m
        """;

    Optional<SettlementDeadLetterRecord> findById(Long id);

    @Query(value = SUMMARY + "where m.state = :state order by m.id desc",
        countQuery = "select count(m) from SettlementDeadLetterRecord m where m.state = :state")
    Page<Summary> findSummaries(State state, Pageable pageable);

    @Query(SUMMARY + "where m.id = :id")
    Optional<Summary> findSummary(Long id);

    long countByState(State state);
}
