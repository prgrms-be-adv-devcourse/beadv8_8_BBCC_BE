package com.bbcc.kidly.boundedcontext.notification.out.deadletter;

import com.bbcc.kidly.boundedcontext.notification.app.port.NotificationDeadLetters;
import com.bbcc.kidly.boundedcontext.notification.domain.WithdrawnMemberRepository;
import com.bbcc.kidly.boundedcontext.notification.out.JpaNotificationDeadLetterRepository;
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
public class NotificationDeadLetterStore implements NotificationDeadLetters {

    private final JdbcTemplate jdbc;
    private final JpaNotificationDeadLetterRepository repository;
    private final WithdrawnMemberRepository withdrawnMemberRepository;

    @Override
    @Transactional
    public boolean insert(Received m, LocalDateTime now) {
        // 탈퇴한 회원의 회원 토픽 원본은 본문(가입 이메일)을 두지 않는다. 탈퇴와 엇갈리지 않게 회원 잠금을 먼저 잡는다
        Long memberId = memberIdOf(m);
        if (memberId != null) {
            withdrawnMemberRepository.lock(memberId);
        }
        return jdbc.update("""
            insert into notification.dead_letter
              (dlt_topic, dlt_partition, dlt_offset, source_topic, source_partition, source_offset,
               message_key, event_type, event_id, payload, failure_type, state, replay_count, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?,
              case when exists (select 1 from notification.withdrawn_member where member_id = cast(? as bigint))
                then null else cast(? as bytea) end,
              ?, 'OPEN', 0, ?, ?)
            on conflict (dlt_topic, dlt_partition, dlt_offset) do nothing
            """, m.dltTopic(), m.dltPartition(), m.dltOffset(), m.sourceTopic(), m.sourcePartition(), m.sourceOffset(),
            m.messageKey(), m.eventType(), m.eventId(), memberId,
            m.payload() == null ? null : m.payload().getBytes(StandardCharsets.UTF_8), m.failureType(), now, now) == 1;
    }

    // 회원 토픽이고 키가 회원 ID일 때만. 그 밖은 null(탈퇴 표시와 맞춰 보지 않는다)
    private static Long memberIdOf(Received m) {
        if (!MEMBER_EVENTS.equals(m.sourceTopic()) || m.messageKey() == null) {
            return null;
        }
        try {
            return Long.valueOf(m.messageKey());
        } catch (NumberFormatException e) {
            return null;
        }
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
    @Transactional
    public int markReprocessed(Long id, Long adminId, LocalDateTime now) {
        return jdbc.update("""
            update notification.dead_letter set state = 'REPROCESSED', replay_count = replay_count + 1,
                last_replayed_by = ?, last_replayed_at = ?, last_replay_failure_type = null, updated_at = ?
            where id = ? and state = 'OPEN'
            """, adminId, now, now, id);
    }

    @Override
    @Transactional
    public int recordReplayFailure(Long id, Long adminId, LocalDateTime now, String failureType) {
        return jdbc.update("""
            update notification.dead_letter set replay_count = replay_count + 1,
                last_replayed_by = ?, last_replayed_at = ?, last_replay_failure_type = ?, updated_at = ?
            where id = ? and state = 'OPEN'
            """, adminId, now, failureType, now, id);
    }

    @Override
    public int erasePayloadOf(String sourceTopic, String messageKey, LocalDateTime now) {
        return jdbc.update("""
            update notification.dead_letter set payload = null, updated_at = ?
            where source_topic = ? and message_key = ? and payload is not null
            """, now, sourceTopic, messageKey);
    }
}
