package com.bbcc.kidly.boundedcontext.review.out.store;

import com.bbcc.kidly.boundedcontext.review.app.port.CommunityDeadLetters;
import com.bbcc.kidly.boundedcontext.review.out.persistence.JpaCommunityDeadLetterRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 원본 저장 오류가 Hibernate의 SQL 오류 로거로 본문을 남기지 않도록 JDBC로 쓴다.
 * 같은 DLT 위치는 ON CONFLICT DO NOTHING, 결과는 OPEN일 때만 원자적으로 갱신한다.
 */
@Component
@RequiredArgsConstructor
public class CommunityDeadLetterStore implements CommunityDeadLetters {

    private final JdbcTemplate jdbc;
    private final JpaCommunityDeadLetterRepository repository;

    @Override
    public boolean insert(Received m, LocalDateTime now) {
        return jdbc.update("""
            insert into community.dead_letter
              (dlt_topic, dlt_partition, dlt_offset, source_topic, source_partition, source_offset,
               message_key, event_type, event_id, payload, failure_type, state, replay_count, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', 0, ?, ?)
            on conflict (dlt_topic, dlt_partition, dlt_offset) do nothing
            """, m.dltTopic(), m.dltPartition(), m.dltOffset(), m.sourceTopic(), m.sourcePartition(), m.sourceOffset(),
            m.messageKey(), m.eventType(), m.eventId(),
            m.payload() == null ? null : m.payload().getBytes(StandardCharsets.UTF_8), m.failureType(), now, now) == 1;
    }

    @Override
    public Page<Summary> findByState(State state, Pageable pageable) {
        return repository.findSummaries(state, pageable);
    }

    @Override
    public long countByState(State state) {
        return repository.countByState(state);
    }

    @Override
    public LocalDateTime findOldestCreatedAt(State state) {
        return repository.findOldestCreatedAt(state);
    }

    @Override
    public Optional<Summary> findSummary(Long id) {
        return repository.findSummary(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Target> findForReplay(Long id) {
        return repository.findById(id).map(m -> new Target(m.getState(),
            new Message(m.getSourceTopic(), m.getMessageKey(), m.getEventType(), m.getEventId(),
                m.getPayload() == null ? null : new String(m.getPayload(), StandardCharsets.UTF_8))));
    }

    @Override
    public int markReprocessed(Long id, Long adminId, LocalDateTime now) {
        return jdbc.update("""
            update community.dead_letter set state = 'REPROCESSED', replay_count = replay_count + 1,
                last_replayed_by = ?, last_replayed_at = ?, last_replay_failure_type = null, updated_at = ?
            where id = ? and state = 'OPEN'
            """, adminId, now, now, id);
    }

    @Override
    public int recordReplayFailure(Long id, Long adminId, LocalDateTime now, String failureType) {
        return jdbc.update("""
            update community.dead_letter set replay_count = replay_count + 1,
                last_replayed_by = ?, last_replayed_at = ?, last_replay_failure_type = ?, updated_at = ?
            where id = ? and state = 'OPEN'
            """, adminId, now, failureType, now, id);
    }
}
