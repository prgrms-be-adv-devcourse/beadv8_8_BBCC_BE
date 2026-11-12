package com.bbcc.kidly.boundedcontext.activity.out.store;

import com.bbcc.kidly.boundedcontext.activity.app.port.EventOutbox;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 이벤트 아웃박스 JDBC 저장(표 community.event_outbox). 넣기는 부르는 쪽 트랜잭션에서 batch 한 번, 릴레이 쪽 쓰기는 문장 하나씩이다.
 */
@Component
@RequiredArgsConstructor
public class EventOutboxStore implements EventOutbox {

    private final JdbcTemplate jdbc;

    @Override
    public void append(List<NewMessage> messages) {
        if (messages.isEmpty()) {
            return;
        }
        jdbc.batchUpdate("""
            insert into community.event_outbox (event_id, event_type, message_key, payload, created_at, attempts,
              next_attempt_at)
            values (?, ?, ?, ?, ?, 0, ?)
            """, messages, messages.size(), (statement, message) -> {
                Timestamp at = Timestamp.valueOf(message.createdAt());
                statement.setObject(1, message.eventId());
                statement.setString(2, message.eventType());
                statement.setString(3, message.key());
                statement.setString(4, message.payload());
                statement.setTimestamp(5, at);
                statement.setTimestamp(6, at);
            });
    }

    @Override
    public List<PendingMessage> findPending(int limit) {
        return jdbc.query("""
            select id, event_id, event_type, message_key, payload, attempts, next_attempt_at
            from community.event_outbox where quarantined_at is null order by id limit ?
            """, (row, index) -> new PendingMessage(row.getLong("id"), row.getObject("event_id", UUID.class),
            row.getString("event_type"), row.getString("message_key"), row.getString("payload"),
            row.getInt("attempts"), row.getTimestamp("next_attempt_at").toLocalDateTime()), limit);
    }

    @Override
    public void delete(long id, UUID eventId) {
        jdbc.update("delete from community.event_outbox where id = ? and event_id = ?", id, eventId);
    }

    @Override
    public void recordFailure(long id, UUID eventId, LocalDateTime nextAttemptAt, String failureType) {
        jdbc.update("update community.event_outbox set attempts = attempts + 1, next_attempt_at = ?, "
            + "last_failure_type = ? where id = ? and event_id = ?", Timestamp.valueOf(nextAttemptAt), failureType,
            id, eventId);
    }

    @Override
    public void quarantine(long id, UUID eventId, LocalDateTime quarantinedAt, String failureType) {
        jdbc.update("update community.event_outbox set attempts = attempts + 1, quarantined_at = ?, "
            + "last_failure_type = ? where id = ? and event_id = ?", Timestamp.valueOf(quarantinedAt), failureType,
            id, eventId);
    }

    @Override
    public long countPending() {
        return jdbc.queryForObject("select count(*) from community.event_outbox where quarantined_at is null",
            Long.class);
    }

    @Override
    public long countQuarantined() {
        return jdbc.queryForObject("select count(*) from community.event_outbox where quarantined_at is not null",
            Long.class);
    }

    @Override
    public LocalDateTime findOldestPendingCreatedAt() {
        Timestamp oldest = jdbc.queryForObject(
            "select min(created_at) from community.event_outbox where quarantined_at is null", Timestamp.class);
        return oldest == null ? null : oldest.toLocalDateTime();
    }
}
