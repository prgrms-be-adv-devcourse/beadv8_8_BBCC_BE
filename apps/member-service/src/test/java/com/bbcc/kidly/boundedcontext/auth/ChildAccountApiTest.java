package com.bbcc.kidly.boundedcontext.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.auth.app.dto.ChildAccountCreateRequest;
import com.bbcc.kidly.boundedcontext.auth.app.dto.ChildAccountResponse;
import com.bbcc.kidly.boundedcontext.auth.app.dto.ChildLoginRequest;
import com.bbcc.kidly.boundedcontext.auth.in.dto.LoginResponse;
import com.bbcc.kidly.boundedcontext.auth.domain.ChildProfileReplicaRepository;
import com.bbcc.kidly.member.support.security.TestMembers;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.IssuedAccessToken;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class ChildAccountApiTest {
    @Autowired
    private RestTestClient client;
    @Autowired
    private ChildProfileReplicaRepository replicas;

    @Test
    void loginReissueAndLogout() {
        replicas.insertIfAbsent(104L, 11L, LocalDateTime.now());
        var account = create(11L, 104L, "child4", "password").expectStatus().isCreated()
            .returnResult(ChildAccountResponse.class).getResponseBody();
        var login = client.post().uri("/api/v1/auth/child/login").contentType(MediaType.APPLICATION_JSON)
            .body(new ChildLoginRequest("child4", "password")).exchange().expectStatus().isOk()
            .returnResult(LoginResponse.class);
        var access = IssuedAccessToken.decode(login.getResponseBody().accessToken());
        assertThat(login.getResponseHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(access.principal().toSubject()).isEqualTo("child:" + account.id());
        assertThat(access.guardianId()).isEqualTo(11L);
        var cookie = login.getResponseCookies().getFirst("child_refresh_token");
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth/child");
        var rotated = client.post().uri("/api/v1/auth/child/reissue").cookie(cookie.getName(), cookie.getValue())
            .exchange().expectStatus().isOk().returnResult().getResponseCookies().getFirst(cookie.getName());
        assertThat(rotated.getValue()).isNotEqualTo(cookie.getValue());
        var logout = client.post().uri("/api/v1/auth/child/logout").cookie(rotated.getName(), rotated.getValue())
            .exchange().expectStatus().isNoContent().returnResult().getResponseCookies().getFirst(cookie.getName());
        assertThat(logout.getMaxAge().isZero()).isTrue();
        client.post().uri("/api/v1/auth/child/reissue").cookie(rotated.getName(), rotated.getValue())
            .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void createsAndValidatesAccount() {
        replicas.insertIfAbsent(101L, 11L, LocalDateTime.now());
        replicas.insertIfAbsent(102L, 11L, LocalDateTime.now());
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
