package com.bbcc.kidly.boundedcontext.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.auth.domain.outbox.AuthOutboxMessage;
import com.bbcc.kidly.boundedcontext.auth.domain.outbox.AuthOutboxRepository;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class AuthOutboxRepositoryIntegrationTest {
    @Autowired private AuthOutboxRepository repository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transactions;

    @Test
    void findsOldRowsAndDeletesSelectedIds() {
        var old = transactions.execute(status -> repository.save(
            AuthOutboxMessage.create(UUID.randomUUID(), "child-account.changed", "1", "{}")));
        var recent = transactions.execute(status -> repository.save(
            AuthOutboxMessage.create(UUID.randomUUID(), "child-account.changed", "2", "{}")));
        jdbc.update("update auth.auth_outbox set created_at = ? where id = ?",
            LocalDateTime.now().minusMinutes(6), old.getId());

        var found = transactions.execute(
            status -> repository.findCreatedBefore(LocalDateTime.now().minusMinutes(5), 100));
        assertThat(found).extracting(AuthOutboxMessage::getId).containsExactly(old.getId());
        transactions.executeWithoutResult(status -> repository.deleteAllByIdInBatch(List.of(old.getId())));
        assertThat(jdbc.queryForList("select id from auth.auth_outbox", Long.class)).containsExactly(recent.getId());
    }
}
