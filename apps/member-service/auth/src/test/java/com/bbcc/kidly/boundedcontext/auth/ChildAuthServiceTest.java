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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class ChildAuthServiceTest {
    private final ChildAccountRepository accounts = mock(ChildAccountRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
    private ChildAuthService service;

    @BeforeEach
    void setUp() {
        when(encoder.encode(anyString())).thenReturn("dummy");
        service = new ChildAuthService(accounts, encoder, refreshTokens, mock(AccessTokenIssuer.class));
    }

    @Test
    void unknownAndWrongPasswordBothRejectAndCompareHash() {
        var request = new ChildLoginRequest("child", "password");
        assertRejected(request);
        verify(encoder).matches("password", "dummy");
        var account = ChildAccount.create("child", "hash", 1L, 2L);
        when(accounts.findByLoginId("child")).thenReturn(Optional.of(account));
        assertRejected(request);
        verify(encoder).matches("password", "hash");
    }

    @Test
    void rejectsAccountDeletedWhileComparingPassword() {
        when(encoder.matches("password", "hash")).thenReturn(true);
        when(accounts.findByLoginId("child")).thenReturn(Optional.of(ChildAccount.create("child", "hash", 1L, 2L)));

        assertRejected(new ChildLoginRequest("child", "password"));
        verifyNoInteractions(refreshTokens);
    }

    @Test
    void rejectsPasswordChangedWhileComparing() {
        when(encoder.matches("password", "hash")).thenReturn(true);
        var account = mock(ChildAccount.class);
        when(account.getId()).thenReturn(1L);
        when(account.getPasswordHash()).thenReturn("hash");
        when(accounts.findByLoginId("child")).thenReturn(Optional.of(account));
        when(accounts.findByIdAndPasswordHashForUpdate(1L, "hash")).thenReturn(Optional.empty());

        assertRejected(new ChildLoginRequest("child", "password"));
        verifyNoInteractions(refreshTokens);
    }

    private void assertRejected(ChildLoginRequest request) {
        assertThatThrownBy(() -> service.login(request)).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_CHILD_CREDENTIALS));
    }
}
