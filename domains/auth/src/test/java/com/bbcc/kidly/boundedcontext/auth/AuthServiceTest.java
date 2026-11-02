package com.bbcc.kidly.boundedcontext.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;

import com.bbcc.kidly.boundedcontext.auth.app.AuthService;
import com.bbcc.kidly.boundedcontext.auth.app.RefreshTokenService;
import com.bbcc.kidly.boundedcontext.auth.app.SocialLoginService;
import com.bbcc.kidly.boundedcontext.auth.app.dto.AuthTokens;
import com.bbcc.kidly.boundedcontext.auth.app.dto.ReissuedToken;
import com.bbcc.kidly.boundedcontext.auth.app.dto.SocialLoginRequest;
import com.bbcc.kidly.boundedcontext.auth.app.port.AccessTokenIssuer;
import com.bbcc.kidly.boundedcontext.auth.app.port.TokenSubject;
import com.bbcc.kidly.boundedcontext.auth.domain.AuthErrorCode;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.auth.Role;
import com.bbcc.kidly.shared.member.dto.MemberLoginState;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final MemberLoginState.Active ACTIVE =
        new MemberLoginState.Active(MEMBER_ID, "테스트회원", List.of(Role.BUYER, Role.SELLER));
    private static final TokenSubject SUBJECT = new TokenSubject(MEMBER_ID, "테스트회원", List.of(Role.BUYER, Role.SELLER));

    @Mock
    private SocialLoginService socialLoginService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AccessTokenIssuer accessTokenIssuer;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("로그인하면 Refresh Token을 발급하고 그 회원의 ID · 닉네임 · 역할로 Access Token을 함께 돌려준다")
    void login() {
        var request = new SocialLoginRequest("code");
        given(socialLoginService.signIn("kakao", request)).willReturn(ACTIVE);
        given(refreshTokenService.issue(MEMBER_ID)).willReturn("refresh");
        given(accessTokenIssuer.issue(SUBJECT)).willReturn("access");

        var tokens = authService.login("kakao", request);

        assertThat(tokens).isEqualTo(new AuthTokens("access", "refresh"));
        var order = inOrder(refreshTokenService, accessTokenIssuer);
        order.verify(refreshTokenService).issue(MEMBER_ID);
        order.verify(accessTokenIssuer).issue(SUBJECT);
    }

    @Test
    @DisplayName("정지 · 탈퇴 회원은 LOGIN_NOT_ALLOWED이고 Refresh Token을 만들지 않는다")
    void loginRejectsInactive() {
        var request = new SocialLoginRequest("code");
        given(socialLoginService.signIn("kakao", request)).willReturn(new MemberLoginState.Suspended(MEMBER_ID));

        assertThatThrownBy(() -> authService.login("kakao", request))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.LOGIN_NOT_ALLOWED));
        then(refreshTokenService).shouldHaveNoInteractions();
        then(accessTokenIssuer).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("재발급하면 Refresh Token을 바꾸고, 확인에 쓴 회원으로 Access Token을 발급한다")
    void reissue() {
        given(refreshTokenService.reissue("old")).willReturn(new ReissuedToken(SUBJECT, "new"));
        given(accessTokenIssuer.issue(SUBJECT)).willReturn("access");

        assertThat(authService.reissue("old")).isEqualTo(new AuthTokens("access", "new"));
    }

    @Test
    @DisplayName("로그아웃하면 Refresh Token을 폐기한다")
    void logout() {
        authService.logout("refresh");

        then(refreshTokenService).should().revoke("refresh");
    }

    @Test
    @DisplayName("쿠키가 없어 토큰이 null이면 아무것도 하지 않는다")
    void logoutWithoutToken() {
        authService.logout(null);

        then(refreshTokenService).shouldHaveNoInteractions();
    }
}
