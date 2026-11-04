package com.bbcc.kidly.boundedcontext.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.auth.app.dto.AdminCreateRequest;
import com.bbcc.kidly.boundedcontext.auth.app.dto.AdminLoginRequest;
import com.bbcc.kidly.boundedcontext.auth.app.dto.AdminResponse;
import com.bbcc.kidly.boundedcontext.auth.domain.AdminAccountRepository;
import com.bbcc.kidly.member.support.security.TestAdmins;
import com.bbcc.kidly.member.support.security.TestMembers;
import com.bbcc.kidly.support.FunctionalTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class AdminAccountApiTest {

    private static final String PATH = "/api/v1/admin/admins";
    private static final String PASSWORD = "password-12345";

    @Autowired
    private RestTestClient client;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private AdminAccountRepository adminAccountRepository;

    private Long actorId;

    @BeforeEach
    void setUp() {
        actorId = adminAccountRepository.saveAccount(AdminAccountFixture.active()).getId();
    }

    @Test
    @DisplayName("관리자를 만들면 201 · Location이고, 만든 관리자 ID를 남기며 그 계정으로 로그인할 수 있다")
    void createsAdminAndAllowsLogin() {
        var result = create(actorId, " New@Kidly.Test ", PASSWORD)
            .expectStatus().isCreated().returnResult(AdminResponse.class);
        var admin = result.getResponseBody();

        assertThat(admin.email()).isEqualTo("new@kidly.test");
        assertThat(result.getResponseHeaders().getFirst(HttpHeaders.LOCATION)).isEqualTo(PATH + "/" + admin.id());
        assertThat(jdbcTemplate.queryForObject("SELECT created_by FROM auth.admin_account WHERE id = ?",
            Long.class, admin.id())).isEqualTo(actorId);
        login(admin.email()).expectStatus().isOk();
    }

    @Test
    @DisplayName("앞뒤 공백 · 대소문자만 다른 이메일은 409다")
    void rejectsDuplicateEmail() {
        create(actorId, "new@kidly.test", PASSWORD).expectStatus().isCreated();

        create(actorId, " NEW@KIDLY.TEST ", PASSWORD).expectStatus().isEqualTo(409);
    }

    @Test
    @DisplayName("비밀번호가 12자보다 짧거나 72바이트보다 길면, 이메일 형식이 아니면 400이다")
    void validatesInput() {
        create(actorId, "test@kidly.test", "12345678901").expectStatus().isBadRequest();
        create(actorId, "test@kidly.test", "a".repeat(73)).expectStatus().isBadRequest();
        create(actorId, "invalid", PASSWORD).expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("관리자 헤더 없이 회원 헤더만 오면 401이다")
    void requiresAdmin() {
        client.post().uri(PATH).headers(TestMembers.buyer(actorId)).contentType(MediaType.APPLICATION_JSON)
            .body(new AdminCreateRequest("test@kidly.test", PASSWORD)).exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("비활성화하면 그 관리자는 로그인 · 재발급이 401이고 Refresh Token 행이 없다. 다시 해도 204다")
    void disableRevokesTokensAndIsIdempotent() {
        var admin = createAdmin("disable@kidly.test");
        var cookie = login(admin.email()).expectStatus().isOk()
            .returnResult().getResponseCookies().getFirst("admin_refresh_token").getValue();

        disable(actorId, admin.id()).expectStatus().isNoContent();
        disable(actorId, admin.id()).expectStatus().isNoContent();

        login(admin.email()).expectStatus().isUnauthorized();
        client.post().uri("/api/v1/auth/admin/reissue").cookie("admin_refresh_token", cookie)
            .exchange().expectStatus().isUnauthorized();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM auth.refresh_token "
            + "WHERE principal_type = 'ADMIN' AND principal_id = ?", Long.class, admin.id())).isZero();
    }

    @Test
    @DisplayName("자기 자신은 409, 없는 관리자는 404다")
    void rejectsSelfAndMissing() {
        disable(actorId, actorId).expectStatus().isEqualTo(409);
        disable(actorId, Long.MAX_VALUE).expectStatus().isNotFound();
    }

    @Test
    @DisplayName("비활성화된 관리자의 아직 무효화되지 않은 토큰으로는 만들거나 비활성화할 수 없다(401)")
    void disabledActorIsRejected() {
        var disabled = createAdmin("disabled@kidly.test");
        disable(actorId, disabled.id()).expectStatus().isNoContent();

        create(disabled.id(), "comeback@kidly.test", PASSWORD).expectStatus().isUnauthorized();
        disable(disabled.id(), actorId).expectStatus().isUnauthorized();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM auth.admin_account WHERE id = ?",
            String.class, actorId)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("두 관리자가 서로를 동시에 비활성화해도 한 명은 활성으로 남는다")
    void concurrentMutualDisableKeepsOneActive() throws Exception {
        var other = createAdmin("other@kidly.test").id();
        var start = new CountDownLatch(1);
        var pairs = List.of(List.of(actorId, other), List.of(other, actorId));

        var statuses = new ArrayList<Integer>();
        try (var executor = Executors.newFixedThreadPool(pairs.size())) {
            var futures = pairs.stream()
                .map(pair -> executor.submit(() -> {
                    start.await();
                    return disable(pair.get(0), pair.get(1)).returnResult().getStatus().value();
                }))
                .toList();
            start.countDown();
            for (Future<Integer> future : futures) {
                statuses.add(future.get());
            }
        }

        assertThat(statuses).containsExactlyInAnyOrder(204, 401);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM auth.admin_account WHERE status = 'ACTIVE'",
            Long.class)).isEqualTo(1L);
    }

    private AdminResponse createAdmin(String email) {
        return create(actorId, email, PASSWORD).expectStatus().isCreated()
            .returnResult(AdminResponse.class).getResponseBody();
    }

    private RestTestClient.ResponseSpec create(Long actor, String email, String password) {
        return client.post().uri(PATH).headers(TestAdmins.of(actor)).contentType(MediaType.APPLICATION_JSON)
            .body(new AdminCreateRequest(email, password)).exchange();
    }

    private RestTestClient.ResponseSpec disable(Long actor, Long adminId) {
        return client.post().uri(PATH + "/" + adminId + "/disable").headers(TestAdmins.of(actor)).exchange();
    }

    private RestTestClient.ResponseSpec login(String email) {
        return client.post().uri("/api/v1/auth/admin/login").contentType(MediaType.APPLICATION_JSON)
            .body(new AdminLoginRequest(email, PASSWORD)).exchange();
    }
}
