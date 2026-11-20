package com.bbcc.kidly.boundedcontext.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.auth.app.ChildAccountService;
import com.bbcc.kidly.boundedcontext.auth.app.dto.ChildAccountCreateRequest;
import com.bbcc.kidly.boundedcontext.auth.domain.ChildProfileReplicaRepository;
import com.bbcc.kidly.member.support.security.TestAdmins;
import com.bbcc.kidly.support.FunctionalTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class AdminChildAccountChangedEventApiTest {

    private static final String PATH = "/api/v1/admin/children/accounts/changed-events";
    private static final String OUTBOX_ROWS = "SELECT message_key, payload FROM auth.auth_outbox "
        + "WHERE event_type = 'child-account.changed' ORDER BY id";

    @Autowired
    private RestTestClient client;
    @Autowired
    private ChildAccountService accounts;
    @Autowired
    private ChildProfileReplicaRepository replicas;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("남아 있는 자녀 계정마다 child-account.changed(deleted=false)가 계정 ID 키로 아웃박스에 쌓이고, 지운 계정은 빠진다")
    void republishLiveAccounts() {
        replicas.insertIfAbsent(201L, 21L, LocalDateTime.now());
        replicas.insertIfAbsent(202L, 21L, LocalDateTime.now());
        var live = accounts.create(21L, new ChildAccountCreateRequest(201L, "child21", "password"));
        var deleted = accounts.create(21L, new ChildAccountCreateRequest(202L, "child22", "password"));
        accounts.delete(21L, deleted.id());
        jdbc.update("DELETE FROM auth.auth_outbox");

        var response = client.post().uri(PATH)
            .headers(TestAdmins.of(100L))
            .exchange()
            .expectStatus().isOk()
            .expectBody(PublishedCount.class)
            .returnResult()
            .getResponseBody();

        assertThat(response.publishedCount()).isEqualTo(1);
        assertThat(jdbc.queryForList(OUTBOX_ROWS)).singleElement().satisfies(row -> {
            assertThat(row.get("message_key")).isEqualTo(live.id().toString());
            assertThat(row.get("payload").toString())
                .contains("\"accountId\":" + live.id(), "\"childProfileId\":201", "\"guardianId\":21",
                    "\"deleted\":false");
        });
    }

    @Test
    @DisplayName("관리자 헤더가 없으면 401이고 아무것도 쌓지 않는다")
    void requiresAdmin() {
        replicas.insertIfAbsent(203L, 21L, LocalDateTime.now());
        accounts.create(21L, new ChildAccountCreateRequest(203L, "child23", "password"));
        jdbc.update("DELETE FROM auth.auth_outbox");

        client.post().uri(PATH).exchange().expectStatus().isUnauthorized();

        assertThat(jdbc.queryForList(OUTBOX_ROWS)).isEmpty();
    }

    private record PublishedCount(int publishedCount) {
    }
}
