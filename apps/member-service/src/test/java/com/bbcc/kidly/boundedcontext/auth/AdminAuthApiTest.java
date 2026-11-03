package com.bbcc.kidly.boundedcontext.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.auth.app.RefreshTokenService;
import com.bbcc.kidly.boundedcontext.auth.app.dto.AdminLoginRequest;
import com.bbcc.kidly.boundedcontext.auth.in.dto.LoginResponse;
import com.bbcc.kidly.boundedcontext.auth.out.JpaAdminAccountRepository;
import com.bbcc.kidly.boundedcontext.auth.out.JpaRefreshTokenRepository;
import com.bbcc.kidly.shared.auth.PrincipalId;
import com.bbcc.kidly.shared.auth.PrincipalType;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.IssuedAccessToken;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

@FunctionalTest
class AdminAuthApiTest {

    private static final String COOKIE = "admin_refresh_token";
    private static final String MEMBER_COOKIE = "refresh_token";
    private static final String LOGIN_PATH = "/api/v1/auth/admin/login";
    private static final String REISSUE_PATH = "/api/v1/auth/admin/reissue";
    private static final String LOGOUT_PATH = "/api/v1/auth/admin/logout";

    @Autowired
    private RestTestClient client;

    @Autowired
    private JpaAdminAccountRepository adminAccountRepository;

    @Autowired
    private JpaRefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("로그인하면 admin_refresh_token을 HttpOnly 쿠키로 받고 응답은 캐시되지 않는다. 회원 쿠키는 싣지 않는다")
    void loginSetsCookie() {
        adminAccountRepository.save(AdminAccountFixture.active());

        login(AdminAccountFixture.EMAIL, AdminAccountFixture.PASSWORD)
            .expectStatus().isOk()
            .expectHeader().valueMatches(HttpHeaders.CACHE_CONTROL, ".*no-store.*")
            .expectCookie().httpOnly(COOKIE, true)
            .expectCookie().secure(COOKIE, true)
            .expectCookie().sameSite(COOKIE, "Lax")
            .expectCookie().path(COOKIE, "/api/v1/auth/admin")
            .expectCookie().maxAge(COOKIE, Duration.ofDays(14))
            .expectCookie().domain(COOKIE, (String) null)
            .expectCookie().doesNotExist(MEMBER_COOKIE);
    }

    @Test
    @DisplayName("발급된 Access Token의 sub는 admin:{id}이고 nickname · roles가 없다")
    void issuedTokenHasAdminSubject() {
        var admin = adminAccountRepository.save(AdminAccountFixture.active());

        var issued = IssuedAccessToken.decode(loginAccessToken());

        assertThat(issued.principal()).isEqualTo(PrincipalId.admin(admin.getId()));
        assertThat(issued.nickname()).isNull();
        assertThat(issued.roles()).isEmpty();
    }

    @Test
    @DisplayName("이메일의 앞뒤 공백 · 대소문자는 무시한다")
    void loginNormalizesEmail() {
        adminAccountRepository.save(AdminAccountFixture.active());

        login(" ADMIN@Kidly.Test ", AdminAccountFixture.PASSWORD).expectStatus().isOk();
    }

    @Test
    @DisplayName("없는 이메일 · 틀린 비밀번호는 같은 401 본문이고 Refresh Token을 만들지 않는다")
    void loginRejectsUnknownEmailAndWrongPassword() {
        adminAccountRepository.save(AdminAccountFixture.active());

        expectInvalidCredentials(login("nobody@kidly.test", AdminAccountFixture.PASSWORD));
        expectInvalidCredentials(login(AdminAccountFixture.EMAIL, "wrong-password-123"));

        assertThat(refreshTokenRepository.count()).isZero();
    }

    @Test
    @DisplayName("비활성 관리자는 비밀번호가 맞아도 같은 401 본문이다")
    void loginRejectsDisabled() {
        adminAccountRepository.save(AdminAccountFixture.disabled());

        expectInvalidCredentials(login(AdminAccountFixture.EMAIL, AdminAccountFixture.PASSWORD));
    }

    @Test
    @DisplayName("이메일 · 비밀번호가 비어 있으면 400")
    void loginValidatesBody() {
        login("", "")
            .expectStatus().isBadRequest()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    @DisplayName("쿠키로 재발급하면 관리자 Access Token과 새 쿠키를 받고 이전 쿠키는 쓸 수 없다")
    void reissue() {
        adminAccountRepository.save(AdminAccountFixture.active());
        var oldToken = loginRefreshToken();

        var result = reissue(oldToken)
            .expectStatus().isOk()
            .expectHeader().valueMatches(HttpHeaders.CACHE_CONTROL, ".*no-store.*")
            .expectCookie().httpOnly(COOKIE, true)
            .expectCookie().path(COOKIE, "/api/v1/auth/admin")
            .returnResult(LoginResponse.class);

        var issued = IssuedAccessToken.decode(result.getResponseBody().accessToken());
        assertThat(issued.principal().type()).isEqualTo(PrincipalType.ADMIN);
        assertThat(result.getResponseCookies().getFirst(COOKIE).getValue()).isNotEqualTo(oldToken);
        expectInvalidRefreshToken(reissue(oldToken));
    }

    @Test
    @DisplayName("회원 쿠키 값을 admin_refresh_token으로 보내면 401이고 회원 토큰은 남는다")
    void memberTokenOnAdminReissue() {
        var memberToken = refreshTokenService.issue(PrincipalId.member(1L));

        expectInvalidRefreshToken(reissue(memberToken));

        assertThat(refreshTokenRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("관리자 쿠키 값을 회원 재발급에 보내면 401이고 관리자 토큰은 그대로 쓸 수 있다")
    void adminTokenOnMemberReissue() {
        adminAccountRepository.save(AdminAccountFixture.active());
        var adminToken = loginRefreshToken();

        expectInvalidRefreshToken(
            client.post().uri("/api/v1/auth/reissue").cookie(MEMBER_COOKIE, adminToken).exchange());

        reissue(adminToken).expectStatus().isOk();
    }

    @Test
    @DisplayName("로그인 뒤 비활성화된 관리자는 재발급할 수 없다")
    void reissueRejectsDisabled() {
        var admin = adminAccountRepository.save(AdminAccountFixture.active());
        var token = loginRefreshToken();
        jdbcTemplate.update("UPDATE auth.admin_account SET status = 'DISABLED' WHERE id = ?", admin.getId());

        expectInvalidRefreshToken(reissue(token));
    }

    @Test
    @DisplayName("쿠키 없이 재발급하면 401")
    void reissueWithoutCookie() {
        expectInvalidRefreshToken(client.post().uri(REISSUE_PATH).exchange());
    }

    @Test
    @DisplayName("로그아웃하면 쿠키를 지우고, 그 토큰으로는 재발급할 수 없다")
    void logout() {
        adminAccountRepository.save(AdminAccountFixture.active());
        var token = loginRefreshToken();

        client.post().uri(LOGOUT_PATH)
            .cookie(COOKIE, token)
            .exchange()
            .expectStatus().isNoContent()
            .expectCookie().maxAge(COOKIE, Duration.ZERO)
            .expectCookie().path(COOKIE, "/api/v1/auth/admin");

        expectInvalidRefreshToken(reissue(token));
    }

    @Test
    @DisplayName("회원 쿠키 값으로 관리자 로그아웃을 불러도 회원 토큰은 지워지지 않는다")
    void logoutIgnoresMemberToken() {
        var memberToken = refreshTokenService.issue(PrincipalId.member(1L));

        client.post().uri(LOGOUT_PATH)
            .cookie(COOKIE, memberToken)
            .exchange()
            .expectStatus().isNoContent();

        assertThat(refreshTokenRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("쿠키 없이 로그아웃해도 204")
    void logoutWithoutCookie() {
        client.post().uri(LOGOUT_PATH)
            .exchange()
            .expectStatus().isNoContent()
            .expectCookie().maxAge(COOKIE, Duration.ZERO);
    }

    private ResponseSpec login(String email, String password) {
        return client.post().uri(LOGIN_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new AdminLoginRequest(email, password))
            .exchange();
    }

    private String loginRefreshToken() {
        return login(AdminAccountFixture.EMAIL, AdminAccountFixture.PASSWORD)
            .expectStatus().isOk()
            .returnResult()
            .getResponseCookies()
            .getFirst(COOKIE)
            .getValue();
    }

    private String loginAccessToken() {
        return login(AdminAccountFixture.EMAIL, AdminAccountFixture.PASSWORD)
            .expectStatus().isOk()
            .returnResult(LoginResponse.class)
            .getResponseBody()
            .accessToken();
    }

    private ResponseSpec reissue(String token) {
        return client.post().uri(REISSUE_PATH).cookie(COOKIE, token).exchange();
    }

    private static void expectInvalidCredentials(ResponseSpec response) {
        response
            .expectStatus().isUnauthorized()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.title").isEqualTo("이메일 또는 비밀번호가 올바르지 않습니다");
    }

    private static void expectInvalidRefreshToken(ResponseSpec response) {
        response
            .expectStatus().isUnauthorized()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.title").isEqualTo("로그인이 만료되었습니다");
    }
}
