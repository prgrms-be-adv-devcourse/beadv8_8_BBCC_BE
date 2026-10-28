package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;

import com.bbcc.kidly.boundedcontext.member.app.AuthService;
import com.bbcc.kidly.boundedcontext.member.app.AuthTokenService;
import com.bbcc.kidly.boundedcontext.member.app.RefreshTokenService;
import com.bbcc.kidly.boundedcontext.member.app.SocialLoginService;
import com.bbcc.kidly.boundedcontext.member.app.dto.AuthTokens;
import com.bbcc.kidly.boundedcontext.member.app.dto.ReissuedToken;
import com.bbcc.kidly.boundedcontext.member.app.dto.SocialLoginRequest;
import com.bbcc.kidly.boundedcontext.member.domain.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Long MEMBER_ID = 1L;

    @Mock
    private SocialLoginService socialLoginService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AuthTokenService authTokenService;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("로그인하면 Refresh Token을 발급하고 그 회원의 Access Token을 함께 돌려준다")
    void login() {
        var member = memberWithId();
        var request = new SocialLoginRequest("code");
        given(socialLoginService.login("kakao", request)).willReturn(member);
        given(refreshTokenService.issue(MEMBER_ID)).willReturn("refresh");
        given(authTokenService.issueAccessToken(member)).willReturn("access");

        var tokens = authService.login("kakao", request);

        assertThat(tokens).isEqualTo(new AuthTokens("access", "refresh"));
        var order = inOrder(refreshTokenService, authTokenService);
        order.verify(refreshTokenService).issue(MEMBER_ID);
        order.verify(authTokenService).issueAccessToken(member);
    }

    @Test
    @DisplayName("재발급하면 Refresh Token을 바꾸고, 확인에 쓴 회원으로 Access Token을 발급한다")
    void reissue() {
        var member = memberWithId();
        given(refreshTokenService.reissue("old")).willReturn(new ReissuedToken(member, "new"));
        given(authTokenService.issueAccessToken(member)).willReturn("access");

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

    private static Member memberWithId() {
        var member = MemberFixture.kakaoMember();
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        return member;
    }
}
