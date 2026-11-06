package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.notification.app.ChildAccountService;
import com.bbcc.kidly.boundedcontext.notification.app.InboxNotificationService;
import com.bbcc.kidly.boundedcontext.notification.app.dto.ChildAccountChangedCommand;
import com.bbcc.kidly.boundedcontext.notification.domain.ChildAccountCopy;
import com.bbcc.kidly.boundedcontext.notification.domain.ChildAccountCopyRepository;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxRecipient;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChildAccountServiceTest {

    @Mock
    private ChildAccountCopyRepository childAccountCopyRepository;

    @Mock
    private InboxNotificationService inboxNotificationService;

    @InjectMocks
    private ChildAccountService childAccountService;

    @Test
    @DisplayName("처음 받은 계정 생성은 활성 계정으로 저장한다")
    void createActive() {
        given(childAccountCopyRepository.findById(30L)).willReturn(Optional.empty());

        childAccountService.apply(new ChildAccountChangedCommand(30L, 3L, false));

        var saved = ArgumentCaptor.forClass(ChildAccountCopy.class);
        verify(childAccountCopyRepository).save(saved.capture());
        assertThat(saved.getValue().getAccountId()).isEqualTo(30L);
        assertThat(saved.getValue().getChildProfileId()).isEqualTo(3L);
        assertThat(saved.getValue().isDeleted()).isFalse();
        verify(inboxNotificationService, never()).deleteAllOf(any());
    }

    @Test
    @DisplayName("삭제된 계정의 생성을 늦게 받아도 되살리지 않는다")
    void keepDeletedOnLateCreate() {
        var copy = ChildAccountCopy.create(30L, 3L, true);
        given(childAccountCopyRepository.findById(30L)).willReturn(Optional.of(copy));

        childAccountService.apply(new ChildAccountChangedCommand(30L, 3L, false));

        assertThat(copy.isDeleted()).isTrue();
        verify(childAccountCopyRepository, never()).save(any());
    }

    @Test
    @DisplayName("계정 삭제는 복사본을 삭제됨으로 바꾸고 그 계정의 자녀 알림을 지운다")
    void deleteWithNotifications() {
        var copy = ChildAccountCopy.create(30L, 3L, false);
        given(childAccountCopyRepository.findById(30L)).willReturn(Optional.of(copy));

        childAccountService.apply(new ChildAccountChangedCommand(30L, 3L, true));

        assertThat(copy.isDeleted()).isTrue();
        verify(inboxNotificationService).deleteAllOf(InboxRecipient.child(30L));
    }

    @Test
    @DisplayName("생성보다 삭제를 먼저 받으면 삭제됨으로 저장해, 늦게 온 생성이 되살리지 않게 한다")
    void deleteBeforeCreate() {
        given(childAccountCopyRepository.findById(30L)).willReturn(Optional.empty());

        childAccountService.apply(new ChildAccountChangedCommand(30L, 3L, true));

        var saved = ArgumentCaptor.forClass(ChildAccountCopy.class);
        verify(childAccountCopyRepository).save(saved.capture());
        assertThat(saved.getValue().isDeleted()).isTrue();
        verify(inboxNotificationService).deleteAllOf(InboxRecipient.child(30L));
    }
}
