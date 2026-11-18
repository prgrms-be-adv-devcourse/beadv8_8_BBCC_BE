package com.bbcc.kidly.boundedcontext.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.auth.domain.outbox.AuthOutboxMessage;
import com.bbcc.kidly.boundedcontext.auth.domain.outbox.AuthOutboxRepository;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class AuthOutboxRepositoryIntegrationTest {
    private static final LocalDateTime SENT_AT = LocalDateTime.of(2026, 11, 18, 12, 0);

    @Autowired private AuthOutboxRepository repository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transactions;

    @Test
    @DisplayName("5분 지난 미전송 메시지만 저장한 순서대로 요청한 건수까지 찾는다")
    void findUnsentInIdOrder() {
        var first = saveUnsent();
        saveSent(SENT_AT);
        var third = saveUnsent();
        var recent = saveUnsent();
        jdbc.update("update auth.auth_outbox set created_at = ? where event_id <> ?",
            LocalDateTime.now().minusMinutes(6), recent);

        List<UUID> found = transactions.execute(status -> repository
            .findUnsentCreatedBefore(LocalDateTime.now().minusMinutes(5), 2).stream()
            .map(AuthOutboxMessage::getEventId)
            .toList());

        assertThat(found).containsExactly(first, third);
    }

    @Test
    @DisplayName("선택한 메시지만 전송 시각과 시도 횟수를 일괄 기록하고, 행은 지우지 않는다")
    void markSentInBatch() {
        var first = saveUnsent();
        var second = saveUnsent();
        var untouched = saveUnsent();
        var ids = jdbc.queryForList("select id from auth.auth_outbox where event_id in (?, ?)",
            Long.class, first, second);

        transactions.executeWithoutResult(status -> repository.markSent(ids, SENT_AT));

        assertThat(jdbc.queryForList("select event_id from auth.auth_outbox "
            + "where sent_at = ? and attempt_count = 1", UUID.class, SENT_AT))
            .containsExactlyInAnyOrder(first, second);
        assertThat(jdbc.queryForList("select event_id from auth.auth_outbox "
            + "where sent_at is null and attempt_count = 0", UUID.class))
            .containsExactly(untouched);
    }

    @Test
    @DisplayName("보내지 못한 기록(시도 횟수 · 마지막 오류)이 행에 남고, 행은 미전송으로 다시 찾힌다")
    void recordedFailureStaysUnsent() {
        var eventId = UUID.randomUUID();
        transactions.executeWithoutResult(status -> {
            var message = AuthOutboxMessageFixture.childAccountChanged(eventId);
            message.recordFailure("eventType=child-account.changed reason=TimeoutException");
            repository.save(message);
        });

        var row = jdbc.queryForMap("select attempt_count, last_error, sent_at from auth.auth_outbox "
            + "where event_id = ?", eventId);
        List<UUID> unsent = transactions.execute(status -> repository
            .findUnsentCreatedBefore(LocalDateTime.now().plusMinutes(1), 100).stream()
            .map(AuthOutboxMessage::getEventId)
            .toList());

        assertThat(row.get("attempt_count")).isEqualTo(1);
        assertThat(row.get("last_error")).isEqualTo("eventType=child-account.changed reason=TimeoutException");
        assertThat(row.get("sent_at")).isNull();
        assertThat(unsent).containsExactly(eventId);
    }

    @Test
    @DisplayName("기준 시각보다 먼저 보낸 메시지만 지우고, 보내지 않은 메시지와 기준 시각 이후에 보낸 메시지는 남긴다")
    void deleteSentBefore() {
        saveSent(SENT_AT);
        var sentAtCutoff = saveSent(SENT_AT.plusDays(1));
        var sentLater = saveSent(SENT_AT.plusDays(2));
        var unsent = saveUnsent();

        Integer deleted = transactions.execute(status -> repository.deleteSentBefore(SENT_AT.plusDays(1)));

        assertThat(deleted).isEqualTo(1);
        assertThat(jdbc.queryForList("select event_id from auth.auth_outbox", UUID.class))
            .containsExactlyInAnyOrder(sentAtCutoff, sentLater, unsent);
    }

    private UUID saveUnsent() {
        var eventId = UUID.randomUUID();
        transactions.executeWithoutResult(status -> repository.save(
            AuthOutboxMessageFixture.childAccountChanged(eventId)));
        return eventId;
    }

    private UUID saveSent(LocalDateTime sentAt) {
        var eventId = UUID.randomUUID();
        transactions.executeWithoutResult(status -> {
            var message = AuthOutboxMessageFixture.childAccountChanged(eventId);
            message.markSent(sentAt);
            repository.save(message);
        });
        return eventId;
    }
}
