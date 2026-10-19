package com.bbcc.kidly.boundedcontext.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.notification.app.NotificationDispatcher;
import com.bbcc.kidly.boundedcontext.notification.app.NotificationSendService;
import com.bbcc.kidly.boundedcontext.notification.app.port.Email;
import com.bbcc.kidly.boundedcontext.notification.app.port.EmailSendException;
import com.bbcc.kidly.boundedcontext.notification.app.port.NotificationEmailSender;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    private static final Long ID = 10L;
    private static final Email EMAIL = new Email("parent@kidly.co.kr", "제목", "본문");

    @Mock
    private NotificationSendService notificationSendService;

    @Mock
    private NotificationEmailSender notificationEmailSender;

    @InjectMocks
    private NotificationDispatcher notificationDispatcher;

    @Test
    @DisplayName("맡을 수 없거나 보낼 주소가 없으면 보내지 않는다")
    void nothingToSend() {
        given(notificationSendService.start(ID)).willReturn(Optional.empty());

        notificationDispatcher.dispatch(ID);

        verify(notificationEmailSender, never()).send(any());
    }

    @Test
    @DisplayName("보내면 성공을 기록한다")
    void send() {
        given(notificationSendService.start(ID)).willReturn(Optional.of(EMAIL));

        notificationDispatcher.dispatch(ID);

        verify(notificationEmailSender).send(EMAIL);
        verify(notificationSendService).recordSent(ID);
    }

    @Test
    @DisplayName("보내지 못하면 재시도 여부와 함께 실패를 기록한다")
    void sendFailed() {
        given(notificationSendService.start(ID)).willReturn(Optional.of(EMAIL));
        willThrow(new EmailSendException("SMTP 인증 실패", false, null)).given(notificationEmailSender).send(EMAIL);

        notificationDispatcher.dispatch(ID);

        verify(notificationSendService).recordFailed(ID, "SMTP 인증 실패", false);
        verify(notificationSendService, never()).recordSent(any());
    }

    @Test
    @DisplayName("다른 곳이 먼저 맡아 버전 충돌이 나면 보내지 않고 끝낸다")
    void lostClaim() {
        given(notificationSendService.start(ID))
            .willThrow(new ObjectOptimisticLockingFailureException("Notification", ID));

        notificationDispatcher.dispatch(ID);

        verify(notificationEmailSender, never()).send(any());
    }
}
