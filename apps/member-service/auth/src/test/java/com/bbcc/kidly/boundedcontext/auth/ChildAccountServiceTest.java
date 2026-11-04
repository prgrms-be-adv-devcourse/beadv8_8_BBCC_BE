package com.bbcc.kidly.boundedcontext.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.auth.app.ChildAccountService;
import com.bbcc.kidly.boundedcontext.auth.app.AccessTokenRevocationService;
import com.bbcc.kidly.boundedcontext.auth.app.dto.ChildAccountCreateRequest;
import com.bbcc.kidly.boundedcontext.auth.domain.ChildAccountRepository;
import com.bbcc.kidly.boundedcontext.auth.domain.ChildProfileReplica;
import com.bbcc.kidly.boundedcontext.auth.domain.ChildProfileReplicaRepository;
import com.bbcc.kidly.boundedcontext.auth.domain.RefreshTokenRepository;
import com.bbcc.kidly.boundedcontext.auth.domain.ChildAccount;
import com.bbcc.kidly.boundedcontext.auth.domain.AuthErrorCode;
import com.bbcc.kidly.member.support.error.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class ChildAccountServiceTest {
    private final ChildProfileReplicaRepository replicas = mock(ChildProfileReplicaRepository.class);
    private final ChildAccountRepository accounts = mock(ChildAccountRepository.class);
    private final ChildAccountService service = new ChildAccountService(replicas, accounts,
        mock(PasswordEncoder.class), mock(RefreshTokenRepository.class), mock(AccessTokenRevocationService.class));
    private final ChildAccountCreateRequest request = new ChildAccountCreateRequest(1L, "child1", "password");

    @Test
    void rejectsMissingOrOtherGuardian() {
        assertThatThrownBy(() -> service.create(2L, request)).isInstanceOf(BusinessException.class);
        var replica = mock(ChildProfileReplica.class);
        when(replica.getGuardianId()).thenReturn(3L);
        when(replicas.findByIdForUpdate(1L)).thenReturn(Optional.of(replica));
        assertThatThrownBy(() -> service.create(2L, request)).isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsDuplicateProfileOrLogin() {
        var replica = mock(ChildProfileReplica.class);
        when(replica.getGuardianId()).thenReturn(2L);
        when(replicas.findByIdForUpdate(1L)).thenReturn(Optional.of(replica));
        when(accounts.existsByChildProfileId(1L)).thenReturn(true, false);
        when(accounts.existsByLoginId("child1")).thenReturn(true);
        assertThatThrownBy(() -> service.create(2L, request)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.create(2L, request)).isInstanceOf(BusinessException.class);
    }

    @Test
    void resetRejectsMissingOrOtherGuardianAndInvalidPassword() {
        assertThatThrownBy(() -> service.resetPassword(2L, 1L, "password"))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> org.assertj.core.api.Assertions.assertThat(e.getErrorCode())
                    .isEqualTo(AuthErrorCode.CHILD_ACCOUNT_NOT_FOUND));
        when(accounts.findByIdForUpdate(1L)).thenReturn(Optional.of(ChildAccount.create("child", "hash", 3L, 4L)));
        assertThatThrownBy(() -> service.resetPassword(2L, 1L, "password"))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.resetPassword(3L, 1L, "short"))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> org.assertj.core.api.Assertions.assertThat(e.getErrorCode())
                    .isEqualTo(AuthErrorCode.INVALID_CHILD_PASSWORD));
    }
}
