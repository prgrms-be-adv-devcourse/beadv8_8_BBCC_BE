package com.bbcc.kidly.boundedcontext.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.auth.app.ChildAccountService;
import com.bbcc.kidly.boundedcontext.auth.app.RefreshTokenService;
import com.bbcc.kidly.boundedcontext.auth.app.dto.ChildAccountCreateRequest;
import com.bbcc.kidly.boundedcontext.auth.app.outbox.AuthEventOutbox;
import com.bbcc.kidly.boundedcontext.auth.domain.ChildAccountRepository;
import com.bbcc.kidly.boundedcontext.auth.domain.ChildProfileReplica;
import com.bbcc.kidly.boundedcontext.auth.domain.ChildProfileReplicaRepository;
import com.bbcc.kidly.boundedcontext.auth.domain.ChildAccount;
import com.bbcc.kidly.boundedcontext.auth.domain.AuthErrorCode;
import com.bbcc.kidly.member.support.error.BusinessException;
import com.bbcc.kidly.shared.auth.event.ChildAccountChangedEvent;
import com.bbcc.kidly.shared.event.DomainEvent;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class ChildAccountServiceTest {
    private final ChildProfileReplicaRepository replicas = mock(ChildProfileReplicaRepository.class);
    private final ChildAccountRepository accounts = mock(ChildAccountRepository.class);
    private final AuthEventOutbox outbox = mock(AuthEventOutbox.class);
    private final ChildAccountService service = new ChildAccountService(replicas, accounts,
        mock(PasswordEncoder.class), mock(RefreshTokenService.class), outbox);
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
                e -> assertThat(e.getErrorCode())
                    .isEqualTo(AuthErrorCode.CHILD_ACCOUNT_NOT_FOUND));
        when(accounts.findByIdForUpdate(1L)).thenReturn(Optional.of(ChildAccount.create("child", "hash", 3L, 4L)));
        assertThatThrownBy(() -> service.resetPassword(2L, 1L, "password"))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.resetPassword(3L, 1L, "short"))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode())
                    .isEqualTo(AuthErrorCode.INVALID_CHILD_PASSWORD));
    }

    @Test
    void republishesEveryStoredAccountAsAliveWithFreshEventIds() {
        var first = ChildAccount.create("child1", "hash", 2L, 10L);
        var second = ChildAccount.create("child2", "hash", 3L, 20L);
        ReflectionTestUtils.setField(first, "id", 1L);
        ReflectionTestUtils.setField(second, "id", 2L);
        when(accounts.findAll()).thenReturn(List.of(first, second));

        assertThat(service.republishAllChanged()).isEqualTo(2);

        var captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outbox, times(2)).append(captor.capture());
        assertThat(captor.getAllValues()).map(ChildAccountChangedEvent.class::cast)
            .extracting(ChildAccountChangedEvent::data)
            .containsExactly(new ChildAccountChangedEvent.Data(1L, 10L, 2L, false),
                new ChildAccountChangedEvent.Data(2L, 20L, 3L, false));
        assertThat(captor.getAllValues().get(0).eventId()).isNotEqualTo(captor.getAllValues().get(1).eventId());
    }
}
