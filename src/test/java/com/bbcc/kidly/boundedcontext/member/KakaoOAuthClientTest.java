package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import com.bbcc.kidly.boundedcontext.member.app.port.OAuthProfile;
import com.bbcc.kidly.boundedcontext.member.domain.MemberErrorCode;
import com.bbcc.kidly.boundedcontext.member.domain.OAuthProvider;
import com.bbcc.kidly.boundedcontext.member.out.KakaoOAuthClient;
import com.bbcc.kidly.boundedcontext.member.out.KakaoProperties;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.ErrorCode;
import java.util.Map;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoOAuthClientTest {

    private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String USER_URL = "https://kapi.kakao.com/v2/user/me";
    private static final KakaoProperties PROPERTIES =
        new KakaoProperties("client-id", "client-secret", "http://localhost:5173/oauth/kakao");

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final KakaoOAuthClient client = new KakaoOAuthClient(builder.build(), PROPERTIES);

    @Test
    @DisplayName("인가 코드로 토큰을 받고 그 토큰으로 사용자 ID와 닉네임을 받는다")
    void fetchProfile() {
        server.expect(requestTo(TOKEN_URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().formDataContains(Map.of(
                "grant_type", "authorization_code",
                "client_id", "client-id",
                "client_secret", "client-secret",
                "redirect_uri", "http://localhost:5173/oauth/kakao",
                "code", "auth-code")))
            .andRespond(withSuccess("{\"access_token\":\"kakao-token\",\"token_type\":\"bearer\"}",
                MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_URL))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer kakao-token"))
            .andRespond(withSuccess("{\"id\":12345,\"kakao_account\":{\"profile\":{\"nickname\":\"카카오닉네임\"}}}",
                MediaType.APPLICATION_JSON));

        var profile = fetch("auth-code");

        assertThat(profile).isEqualTo(new OAuthProfile("12345", "카카오닉네임"));
        server.verify();
    }

    @Test
    @DisplayName("닉네임 제공에 동의하지 않았으면 닉네임 없이 돌려준다")
    void noNickname() {
        expectToken();
        server.expect(requestTo(USER_URL))
            .andRespond(withSuccess("{\"id\":12345,\"kakao_account\":{}}", MediaType.APPLICATION_JSON));

        assertThat(fetch("auth-code")).isEqualTo(new OAuthProfile("12345", null));
    }

    @Test
    @DisplayName("잘못되거나 이미 쓴 인가 코드면 INVALID_AUTHORIZATION_CODE")
    void invalidGrant() {
        server.expect(requestTo(TOKEN_URL))
            .andRespond(withBadRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"invalid_grant\",\"error_code\":\"KOE320\"}"));

        assertErrorCode(() -> fetch("used-code"), MemberErrorCode.INVALID_AUTHORIZATION_CODE);
    }

    @Test
    @DisplayName("redirect URI가 등록된 값과 달라 400이 나면 OAUTH_PROVIDER_ERROR")
    void badRequestOtherThanInvalidGrant() {
        server.expect(requestTo(TOKEN_URL))
            .andRespond(withBadRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"invalid_request\",\"error_code\":\"KOE006\"}"));

        assertErrorCode(() -> fetch("auth-code"), MemberErrorCode.OAUTH_PROVIDER_ERROR);
    }

    @Test
    @DisplayName("앱 키나 secret 설정이 잘못돼 토큰을 받지 못하면 OAUTH_PROVIDER_ERROR")
    void invalidClient() {
        server.expect(requestTo(TOKEN_URL))
            .andRespond(withUnauthorizedRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"invalid_client\",\"error_code\":\"KOE010\"}"));

        assertErrorCode(() -> fetch("auth-code"), MemberErrorCode.OAUTH_PROVIDER_ERROR);
    }

    @Test
    @DisplayName("사용자 정보 조회가 실패하면 OAUTH_PROVIDER_ERROR")
    void userInfoFailure() {
        expectToken();
        server.expect(requestTo(USER_URL)).andRespond(withServerError());

        assertErrorCode(() -> fetch("auth-code"), MemberErrorCode.OAUTH_PROVIDER_ERROR);
    }

    private OAuthProfile fetch(String code) {
        return client.fetchProfile(OAuthProvider.KAKAO, code);
    }

    private void expectToken() {
        server.expect(requestTo(TOKEN_URL))
            .andRespond(withSuccess("{\"access_token\":\"kakao-token\"}", MediaType.APPLICATION_JSON));
    }

    private static void assertErrorCode(ThrowingCallable call, ErrorCode errorCode) {
        assertThatThrownBy(call)
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(errorCode));
    }
}
