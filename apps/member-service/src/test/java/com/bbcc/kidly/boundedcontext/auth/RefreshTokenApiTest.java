package com.bbcc.kidly.boundedcontext.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.auth.app.dto.SocialLoginRequest;
import com.bbcc.kidly.boundedcontext.auth.domain.SocialAccountRepository;
import com.bbcc.kidly.boundedcontext.auth.in.dto.LoginResponse;
import com.bbcc.kidly.boundedcontext.member.MemberFixture;
import com.bbcc.kidly.boundedcontext.member.app.dto.MemberUpdateRequest;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.shared.auth.Role;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.IssuedAccessToken;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class RefreshTokenApiTest {

    private static final String COOKIE = "refresh_token";
    private static final String LOGIN_PATH = "/api/v1/auth/login/kakao";
    private static final String REISSUE_PATH = "/api/v1/auth/reissue";
    private static final String LOGOUT_PATH = "/api/v1/auth/logout";

    @Autowired
    private RestTestClient client;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Test
    @DisplayName("로그인하면 Refresh Token을 HttpOnly 쿠키로 받고 응답은 캐시되지 않는다")
    void loginSetsCookie() {
        client.post().uri(LOGIN_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new SocialLoginRequest("kakao-1"))
            .exchange()
            .expectStatus().isOk()
            .expectHeader().valueMatches(HttpHeaders.CACHE_CONTROL, ".*no-store.*")
            .expectCookie().httpOnly(COOKIE, true)
            .expectCookie().secure(COOKIE, true)
            .expectCookie().sameSite(COOKIE, "Lax")
            .expectCookie().path(COOKIE, "/api/v1/auth")
            .expectCookie().maxAge(COOKIE, Duration.ofDays(14))
            .expectCookie().domain(COOKIE, (String) null);
    }

    @Test
    @DisplayName("쿠키로 재발급하면 새 Access Token과 새 쿠키를 받는다")
    void reissue() {
        var oldToken = login();

        var result = reissue(oldToken)
            .expectStatus().isOk()
            .expectHeader().valueMatches(HttpHeaders.CACHE_CONTROL, ".*no-store.*")
            .expectCookie().httpOnly(COOKIE, true)
            .returnResult(LoginResponse.class);

        assertThat(result.getResponseBody().accessToken()).isNotBlank();
        assertThat(result.getResponseCookies().getFirst(COOKIE).getValue()).isNotEqualTo(oldToken);
    }

    @Test
    @DisplayName("닉네임을 바꾼 뒤 재발급하면 새 Access Token의 nickname 클레임이 바뀐 닉네임이다")
    void reissueReflectsNicknameChange() {
        var loginResult = client.post().uri(LOGIN_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new SocialLoginRequest("kakao-1"))
            .exchange()
            .expectStatus().isOk()
            .returnResult(LoginResponse.class);
        var oldRefreshToken = loginResult.getResponseCookies().getFirst(COOKIE).getValue();
        var issued = IssuedAccessToken.decode(loginResult.getResponseBody().accessToken());

        client.patch().uri("/api/v1/members/me")
            .headers(issued.headers())
            .contentType(MediaType.APPLICATION_JSON)
            .body(new MemberUpdateRequest("새닉네임"))
            .exchange()
            .expectStatus().isOk();

        var reissuedToken = reissue(oldRefreshToken)
            .expectStatus().isOk()
            .returnResult(LoginResponse.class)
            .getResponseBody()
            .accessToken();

        assertThat(IssuedAccessToken.decode(reissuedToken).nickname()).isEqualTo("새닉네임");
    }

    @Test
    @DisplayName("판매자 자격을 잃은 뒤 재발급하면 새 Access Token 역할에서 SELLER가 빠진다")
    void reissueReflectsSellerLoss() {
        var member = memberRepository.save(MemberFixture.withSellerState("kakao-seller", SellerStatus.ACTIVE));
        socialAccountRepository.save(SocialAccountFixture.linked(member.getId(), "kakao-seller"));
        var loginResult = client.post().uri(LOGIN_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new SocialLoginRequest("kakao-seller"))
            .exchange()
            .expectStatus().isOk()
            .returnResult(LoginResponse.class);
        var oldRefreshToken = loginResult.getResponseCookies().getFirst(COOKIE).getValue();
        assertThat(IssuedAccessToken.decode(loginResult.getResponseBody().accessToken()).roles())
            .contains(Role.SELLER);

        member.applySellerState(SellerStatus.SUSPENDED, LocalDateTime.now());
        memberRepository.save(member);

        var reissuedToken = reissue(oldRefreshToken)
            .expectStatus().isOk()
            .returnResult(LoginResponse.class)
            .getResponseBody()
            .accessToken();

        assertThat(IssuedAccessToken.decode(reissuedToken).roles()).doesNotContain(Role.SELLER);
    }

    @Test
    @DisplayName("이미 재발급에 쓴 쿠키로는 다시 재발급할 수 없다")
    void reusedToken() {
        var oldToken = login();
        reissue(oldToken).expectStatus().isOk();

        expectInvalid(reissue(oldToken));
    }

    @Test
    @DisplayName("쿠키 없이 재발급하면 401")
    void reissueWithoutCookie() {
        expectInvalid(client.post().uri(REISSUE_PATH).exchange());
    }

    @Test
    @DisplayName("로그아웃하면 쿠키를 지우고, 그 토큰으로는 재발급할 수 없다")
    void logout() {
        var token = login();

        client.post().uri(LOGOUT_PATH)
            .cookie(COOKIE, token)
            .exchange()
            .expectStatus().isNoContent()
            .expectCookie().maxAge(COOKIE, Duration.ZERO)
            .expectCookie().path(COOKIE, "/api/v1/auth");

        expectInvalid(reissue(token));
    }

    @Test
    @DisplayName("탈퇴한 뒤 로그아웃하면 쿠키를 지우고, 그 토큰으로는 재발급할 수 없다")
    void logoutAfterWithdraw() {
        var loginResult = client.post().uri(LOGIN_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new SocialLoginRequest("kakao-1"))
            .exchange()
            .expectStatus().isOk()
            .returnResult(LoginResponse.class);
        var token = loginResult.getResponseCookies().getFirst(COOKIE).getValue();
        var issued = IssuedAccessToken.decode(loginResult.getResponseBody().accessToken());

        client.delete().uri("/api/v1/members/me")
            .headers(issued.headers())
            .exchange()
            .expectStatus().isNoContent();

        client.post().uri(LOGOUT_PATH)
            .cookie(COOKIE, token)
            .exchange()
            .expectStatus().isNoContent()
            .expectCookie().maxAge(COOKIE, Duration.ZERO)
            .expectCookie().path(COOKIE, "/api/v1/auth");

        expectInvalid(reissue(token));
    }

    @Test
    @DisplayName("쿠키 없이 로그아웃해도 204")
    void logoutWithoutCookie() {
        client.post().uri(LOGOUT_PATH)
            .exchange()
            .expectStatus().isNoContent()
            .expectCookie().maxAge(COOKIE, Duration.ZERO);
    }

    // 로그인 응답의 Refresh Token 쿠키 값을 돌려준다
    private String login() {
        return client.post().uri(LOGIN_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new SocialLoginRequest("kakao-1"))
            .exchange()
            .expectStatus().isOk()
            .returnResult()
            .getResponseCookies()
            .getFirst(COOKIE)
            .getValue();
    }

    private ResponseSpec reissue(String token) {
        return client.post().uri(REISSUE_PATH).cookie(COOKIE, token).exchange();
    }

    private void expectInvalid(ResponseSpec response) {
        response
            .expectStatus().isUnauthorized()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.title").isEqualTo("로그인이 만료되었습니다");
    }
}
