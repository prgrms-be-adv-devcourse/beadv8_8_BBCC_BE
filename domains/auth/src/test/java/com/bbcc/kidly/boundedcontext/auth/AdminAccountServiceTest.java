package com.bbcc.kidly.boundedcontext.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.auth.app.AccessTokenRevocationService;
import com.bbcc.kidly.boundedcontext.auth.app.AdminAccountService;
import com.bbcc.kidly.boundedcontext.auth.domain.AdminAccountRepository;
import com.bbcc.kidly.boundedcontext.auth.domain.AdminStatus;
import com.bbcc.kidly.boundedcontext.auth.domain.AuthErrorCode;
import com.bbcc.kidly.boundedcontext.auth.domain.RefreshTokenRepository;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.CommonErrorCode;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.auth.PrincipalId;
import com.bbcc.kidly.shared.auth.PrincipalType;
import com.bbcc.kidly.shared.auth.event.AuthTokensRevokedEvent;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class AdminAccountServiceTest {

    @Mock
    private AdminAccountRepository accounts;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RefreshTokenRepository refreshTokens;
    @Mock
    private AccessTokenRevocationService revocations;
    @InjectMocks
    private AdminAccountService service;

    @Test
    @DisplayName("처음 비활성화할 때만 Refresh Token을 지우고 Access Token을 무효화한다")
    void disableRevokesOnlyOnce() {
        givenAdmins(1L, 2L);

        service.disable(1L, 2L);
        service.disable(1L, 2L);

        verify(refreshTokens, times(1)).deleteByPrincipal(PrincipalId.admin(2L));
        verify(revocations, times(1)).revoke(eq(PrincipalId.admin(2L)), any());
    }

    @Test
    @DisplayName("없는 관리자를 비활성화하면 404다")
    void missingAdminReturnsNotFound() {
        givenAdmins(1L);
        when(accounts.findByIdForUpdate(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.disable(1L, 2L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(AuthErrorCode.ADMIN_NOT_FOUND);
    }

    @Test
    @DisplayName("호출한 관리자가 이미 비활성이면 401이고 아무것도 바꾸지 않는다")
    void disabledActorCannotDisable() {
        var actor = AdminAccountFixture.active(1L);
        ReflectionTestUtils.setField(actor, "status", AdminStatus.DISABLED);
        when(accounts.findByIdForUpdate(1L)).thenReturn(Optional.of(actor));

        assertThatThrownBy(() -> service.disable(1L, 2L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.UNAUTHORIZED);
        verify(refreshTokens, never()).deleteByPrincipal(any());
    }

    @Test
    @DisplayName("호출한 관리자와 대상의 행을 언제나 ID가 작은 쪽부터 잠근다")
    void locksInIdOrder() {
        givenAdmins(1L, 2L);

        service.disable(2L, 1L);

        var order = inOrder(accounts);
        order.verify(accounts).findByIdForUpdate(1L);
        order.verify(accounts).findByIdForUpdate(2L);
    }

    @Test
    @DisplayName("비활성화하면 ADMIN 주체의 auth.tokens-revoked를 한 번만 발행한다")
    void disablePublishesOneAdminRevocation() {
        var publisher = mock(EventPublisher.class);
        var actualService = new AdminAccountService(accounts, passwordEncoder, refreshTokens,
            new AccessTokenRevocationService(publisher));
        givenAdmins(1L, 2L);
        TransactionSynchronizationManager.initSynchronization();
        try {
            actualService.disable(1L, 2L);
            actualService.disable(1L, 2L);

            verify(publisher, times(1)).publish(argThat(event ->
                event instanceof AuthTokensRevokedEvent revoked
                    && revoked.data().principalType() == PrincipalType.ADMIN
                    && revoked.data().principalId().equals(2L)));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void givenAdmins(Long... ids) {
        for (var id : ids) {
            when(accounts.findByIdForUpdate(id)).thenReturn(Optional.of(AdminAccountFixture.active(id)));
        }
    }
}
