package com.bbcc.kidly.boundedcontext.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;

import com.bbcc.kidly.boundedcontext.auth.app.AdminAuthService;
import com.bbcc.kidly.boundedcontext.auth.app.RefreshTokenService;
import com.bbcc.kidly.boundedcontext.auth.app.dto.AdminLoginRequest;
import com.bbcc.kidly.boundedcontext.auth.app.dto.AuthTokens;
import com.bbcc.kidly.boundedcontext.auth.app.dto.ReissuedToken;
import com.bbcc.kidly.boundedcontext.auth.app.port.AccessTokenIssuer;
import com.bbcc.kidly.boundedcontext.auth.app.port.TokenSubject;
import com.bbcc.kidly.boundedcontext.auth.domain.AdminAccountRepository;
import com.bbcc.kidly.boundedcontext.auth.domain.AuthErrorCode;
import com.bbcc.kidly.member.support.error.BusinessException;
import com.bbcc.kidly.shared.auth.PrincipalId;
import com.bbcc.kidly.shared.auth.PrincipalType;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceTest {

    private static final Long ADMIN_ID = 7L;
    private static final TokenSubject SUBJECT = new TokenSubject.Admin(ADMIN_ID);
    private static final AdminLoginRequest REQUEST =
        new AdminLoginRequest(" Admin@Kidly.Test ", AdminAccountFixture.PASSWORD);

    @Mock
    private AdminAccountRepository adminAccountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AccessTokenIssuer accessTokenIssuer;

    @InjectMocks
    private AdminAuthService adminAuthService;

    @Test
    @DisplayName("로그인하면 정규화한 이메일로 행을 잠가 찾고, Refresh Token을 발급한 뒤 admin:{id}로 Access Token을 낸다")
    void login() {
        var admin = AdminAccountFixture.active(ADMIN_ID);
        given(adminAccountRepository.findByEmailForUpdate(AdminAccountFixture.EMAIL)).willReturn(Optional.of(admin));
        given(passwordEncoder.matches(AdminAccountFixture.PASSWORD, admin.getPasswordHash())).willReturn(true);
        given(refreshTokenService.issue(PrincipalId.admin(ADMIN_ID))).willReturn("refresh");
        given(accessTokenIssuer.issue(SUBJECT)).willReturn("access");

        var tokens = adminAuthService.login(REQUEST);

        assertThat(tokens).isEqualTo(new AuthTokens("access", "refresh"));
        var order = inOrder(refreshTokenService, accessTokenIssuer);
        order.verify(refreshTokenService).issue(PrincipalId.admin(ADMIN_ID));
        order.verify(accessTokenIssuer).issue(SUBJECT);
    }

    @Test
    @DisplayName("없는 이메일이어도 해시 비교를 한 번 하고 INVALID_ADMIN_CREDENTIALS다")
    void unknownEmail() {
        given(adminAccountRepository.findByEmailForUpdate(AdminAccountFixture.EMAIL)).willReturn(Optional.empty());

        assertRejected(() -> adminAuthService.login(REQUEST));

        then(passwordEncoder).should().matches(eq(AdminAccountFixture.PASSWORD), any());
        then(refreshTokenService).shouldHaveNoInteractions();
        then(accessTokenIssuer).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("비밀번호가 틀리면 INVALID_ADMIN_CREDENTIALS다")
    void wrongPassword() {
        var admin = AdminAccountFixture.active(ADMIN_ID);
        given(adminAccountRepository.findByEmailForUpdate(AdminAccountFixture.EMAIL)).willReturn(Optional.of(admin));
        given(passwordEncoder.matches(AdminAccountFixture.PASSWORD, admin.getPasswordHash())).willReturn(false);

        assertRejected(() -> adminAuthService.login(REQUEST));

        then(refreshTokenService).shouldHaveNoInteractions();
        then(accessTokenIssuer).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("비활성 관리자는 비밀번호가 맞아도 INVALID_ADMIN_CREDENTIALS다")
    void disabledAdmin() {
        var admin = AdminAccountFixture.disabled();
        given(adminAccountRepository.findByEmailForUpdate(AdminAccountFixture.EMAIL)).willReturn(Optional.of(admin));
        given(passwordEncoder.matches(AdminAccountFixture.PASSWORD, admin.getPasswordHash())).willReturn(true);

        assertRejected(() -> adminAuthService.login(REQUEST));

        then(refreshTokenService).shouldHaveNoInteractions();
        then(accessTokenIssuer).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("재발급은 관리자 종류로 Refresh Token을 바꾸고, 확인에 쓴 관리자로 Access Token을 낸다")
    void reissue() {
        given(refreshTokenService.reissue(PrincipalType.ADMIN, "old")).willReturn(new ReissuedToken(SUBJECT, "new"));
        given(accessTokenIssuer.issue(SUBJECT)).willReturn("access");

        assertThat(adminAuthService.reissue("old")).isEqualTo(new AuthTokens("access", "new"));
    }

    @Test
    @DisplayName("로그아웃하면 관리자 종류로 Refresh Token을 폐기한다")
    void logout() {
        adminAuthService.logout("refresh");

        then(refreshTokenService).should().revoke(PrincipalType.ADMIN, "refresh");
    }

    @Test
    @DisplayName("쿠키가 없어 토큰이 null이면 아무것도 하지 않는다")
    void logoutWithoutToken() {
        adminAuthService.logout(null);

        then(refreshTokenService).shouldHaveNoInteractions();
    }

    private static void assertRejected(ThrowingCallable call) {
        assertThatThrownBy(call)
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_ADMIN_CREDENTIALS));
    }
}
