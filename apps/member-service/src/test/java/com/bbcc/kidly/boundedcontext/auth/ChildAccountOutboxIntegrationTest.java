package com.bbcc.kidly.boundedcontext.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.auth.app.ChildAccountService;
import com.bbcc.kidly.boundedcontext.auth.app.ChildProfileReplicaService;
import com.bbcc.kidly.boundedcontext.auth.app.dto.ChildAccountCreateRequest;
import com.bbcc.kidly.boundedcontext.auth.domain.ChildProfileReplicaRepository;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class ChildAccountOutboxIntegrationTest {
    @Autowired
    private ChildAccountService accounts;
    @Autowired
    private ChildProfileReplicaRepository replicas;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ChildProfileReplicaService replicaService;

    @Test
    void recordsCreationAndDeletionWithAccountKey() {
        replicas.insertIfAbsent(101L, 11L, LocalDateTime.now());
        var account = accounts.create(11L, new ChildAccountCreateRequest(101L, "child1", "password"));
        accounts.delete(11L, account.id());

        var rows = jdbc.queryForList("SELECT message_key, payload FROM auth.auth_outbox ORDER BY id");
        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(row -> assertThat(row.get("message_key")).isEqualTo(account.id().toString()));
        assertThat(rows.getFirst().get("payload").toString()).contains("\"deleted\":false");
        assertThat(rows.getLast().get("payload").toString()).contains("\"deleted\":true");
    }

    @Test
    void recordsDeletionFromProfileEventAndRejectsDuplicateLogin() {
        replicas.insertIfAbsent(102L, 11L, LocalDateTime.now());
        replicas.insertIfAbsent(103L, 11L, LocalDateTime.now());
        var account = accounts.create(11L, new ChildAccountCreateRequest(102L, "child2", "password"));
        assertThatThrownBy(() -> accounts.create(11L, new ChildAccountCreateRequest(103L, "child2", "password")))
            .isInstanceOf(RuntimeException.class);
        replicaService.apply(102L, 11L, true);
        var rows = jdbc.queryForList("SELECT message_key, payload FROM auth.auth_outbox ORDER BY id");
        assertThat(rows).hasSize(2);
        assertThat(rows.getLast().get("message_key")).isEqualTo(account.id().toString());
        assertThat(rows.getLast().get("payload").toString()).contains("\"deleted\":true");
    }
}
