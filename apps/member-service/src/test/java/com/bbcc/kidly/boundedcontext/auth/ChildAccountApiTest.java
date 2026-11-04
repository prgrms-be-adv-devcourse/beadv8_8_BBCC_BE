package com.bbcc.kidly.boundedcontext.auth;

import com.bbcc.kidly.boundedcontext.auth.app.dto.ChildAccountCreateRequest;
import com.bbcc.kidly.member.support.security.TestMembers;
import com.bbcc.kidly.support.FunctionalTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class ChildAccountApiTest {
    @Autowired
    private RestTestClient client;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createsAndValidatesAccount() {
        jdbc.update("INSERT INTO auth.child_profile_replica VALUES (101, 11, now(), now())");
        jdbc.update("INSERT INTO auth.child_profile_replica VALUES (102, 11, now(), now())");
        create(11L, 101L, "child1", "password").expectStatus().isCreated();
        create(11L, 101L, "child2", "password").expectStatus().isEqualTo(409);
        create(11L, 102L, "child1", "password").expectStatus().isEqualTo(409);
        create(12L, 102L, "child2", "password").expectStatus().isNotFound();
        create(11L, 103L, "child2", "password").expectStatus().isNotFound();
        create(11L, 102L, "CHILD", "password").expectStatus().isBadRequest();
        create(11L, 102L, "child2", "short").expectStatus().isBadRequest();
        client.post().uri("/api/v1/auth/child-accounts").contentType(MediaType.APPLICATION_JSON)
            .body(new ChildAccountCreateRequest(102L, "child2", "password")).exchange()
            .expectStatus().isUnauthorized();
    }

    private RestTestClient.ResponseSpec create(Long guardian, Long profile, String login, String password) {
        return client.post().uri("/api/v1/auth/child-accounts").headers(TestMembers.buyer(guardian))
            .contentType(MediaType.APPLICATION_JSON).body(new ChildAccountCreateRequest(profile, login, password))
            .exchange();
    }
}
