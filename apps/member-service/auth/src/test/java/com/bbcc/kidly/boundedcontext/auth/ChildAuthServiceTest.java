package com.bbcc.kidly.boundedcontext.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.auth.app.ChildAuthService;
import com.bbcc.kidly.boundedcontext.auth.app.RefreshTokenService;
import com.bbcc.kidly.boundedcontext.auth.app.dto.ChildLoginRequest;
import com.bbcc.kidly.boundedcontext.auth.app.port.AccessTokenIssuer;
import com.bbcc.kidly.boundedcontext.auth.domain.ChildAccount;
import com.bbcc.kidly.boundedcontext.auth.domain.ChildAccountRepository;
import com.bbcc.kidly.boundedcontext.auth.domain.AuthErrorCode;
import com.bbcc.kidly.member.support.error.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class ChildAuthServiceTest {
    @Test
    void unknownAndWrongPasswordBothRejectAndCompareHash() {
        var accounts = mock(ChildAccountRepository.class);
        var encoder = mock(PasswordEncoder.class);
        when(encoder.encode(anyString())).thenReturn("dummy");
        var service = new ChildAuthService(accounts, encoder, mock(RefreshTokenService.class),
            mock(AccessTokenIssuer.class));
        var request = new ChildLoginRequest("child", "password");
        assertThatThrownBy(() -> service.login(request)).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_CHILD_CREDENTIALS));
        verify(encoder).matches("password", "dummy");
        var account = ChildAccount.create("child", "hash", 1L, 2L);
        when(accounts.findByLoginId("child")).thenReturn(Optional.of(account));
        assertThatThrownBy(() -> service.login(request)).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_CHILD_CREDENTIALS));
        verify(encoder).matches("password", "hash");
    }

    @Test
    void rejectsAccountDeletedWhileComparingPassword() {
        var accounts = mock(ChildAccountRepository.class);
        var encoder = mock(PasswordEncoder.class);
        when(encoder.encode(anyString())).thenReturn("dummy");
        when(encoder.matches("password", "hash")).thenReturn(true);
        when(accounts.findByLoginId("child")).thenReturn(Optional.of(ChildAccount.create("child", "hash", 1L, 2L)));
        var refreshTokens = mock(RefreshTokenService.class);
        var service = new ChildAuthService(accounts, encoder, refreshTokens, mock(AccessTokenIssuer.class));

        assertThatThrownBy(() -> service.login(new ChildLoginRequest("child", "password")))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_CHILD_CREDENTIALS));
        verifyNoInteractions(refreshTokens);
    }
}
