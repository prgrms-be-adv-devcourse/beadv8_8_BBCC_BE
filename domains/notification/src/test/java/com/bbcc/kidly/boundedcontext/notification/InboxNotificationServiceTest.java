package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.notification.app.InboxNotificationService;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotificationRepository;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxRecipient;
import com.bbcc.kidly.shared.auth.PrincipalId;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InboxNotificationServiceTest {

    @Mock
    private InboxNotificationRepository inboxNotificationRepository;

    @InjectMocks
    private InboxNotificationService inboxNotificationService;

    @Test
    @DisplayName("모두 읽음은 내 안 읽은 알림을 지금 시각으로 한 번에 읽음 처리한다")
    void readAll() {
        var before = LocalDateTime.now();

        inboxNotificationService.readAll(PrincipalId.member(1L));

        var now = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(inboxNotificationRepository).readAllUnread(eq(InboxRecipient.member(1L)), now.capture());
        assertThat(now.getValue()).isBetween(before, LocalDateTime.now());
    }

    @Test
    @DisplayName("로그인 주체의 종류와 ID로 알림함 받는 사람을 정하고, 관리자는 알림함이 없다")
    void recipientOfPrincipal() {
        assertThat(InboxRecipient.of(PrincipalId.member(7L))).isEqualTo(InboxRecipient.member(7L));
        assertThat(InboxRecipient.of(PrincipalId.child(7L))).isEqualTo(InboxRecipient.child(7L));
        assertThatThrownBy(() -> InboxRecipient.of(PrincipalId.admin(7L)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("정리는 만든 지 90일이 지난 알림을 지우고 지운 개수를 준다")
    void deleteExpired() {
        given(inboxNotificationRepository.deleteCreatedBefore(any())).willReturn(3);
        var before = LocalDateTime.now();

        var deleted = inboxNotificationService.deleteExpired();

        var cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(inboxNotificationRepository).deleteCreatedBefore(cutoff.capture());
        assertThat(deleted).isEqualTo(3);
        assertThat(cutoff.getValue())
            .isBetween(before.minus(Duration.ofDays(90)), LocalDateTime.now().minus(Duration.ofDays(90)));
    }
}
