package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.notification.app.NotificationDispatcher;
import com.bbcc.kidly.boundedcontext.notification.app.NotificationProperties;
import com.bbcc.kidly.boundedcontext.notification.app.NotificationService;
import com.bbcc.kidly.boundedcontext.notification.app.dto.WelcomeEmailCommand;
import com.bbcc.kidly.boundedcontext.notification.domain.Notification;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationChannel;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationRepository;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationStatus;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationType;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final WelcomeEmailCommand COMMAND =
        new WelcomeEmailCommand(EVENT_ID, 1L, "parent@kidly.co.kr");

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationDispatcher notificationDispatcher;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, notificationDispatcher,
            new NotificationProperties("https://kidly.kro.kr"));
        // 서비스는 @Transactional 안에서 불리므로, 커밋 뒤 콜백을 등록할 수 있게 동기화를 켠다
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    @DisplayName("환영 메일을 PENDING으로 저장하고, 커밋 뒤에 비동기 발송을 넘긴다")
    void createWelcomeEmail() {
        given(notificationRepository.existsByEventIdAndChannel(EVENT_ID, NotificationChannel.EMAIL))
            .willReturn(false);
        given(notificationRepository.save(any())).willAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            ReflectionTestUtils.setField(notification, "id", 10L);
            return notification;
        });

        notificationService.createWelcomeEmail(COMMAND);

        var saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(NotificationType.WELCOME);
        assertThat(saved.getValue().getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(saved.getValue().getRecipient()).isEqualTo("parent@kidly.co.kr");
        assertThat(saved.getValue().getBody()).contains("https://kidly.kro.kr");
        verify(notificationDispatcher, never()).dispatchAsync(any());

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(notificationDispatcher).dispatchAsync(10L);
    }

    @Test
    @DisplayName("같은 가입 이벤트로 만든 환영 메일이 있으면 만들지 않는다")
    void duplicateEvent() {
        given(notificationRepository.existsByEventIdAndChannel(EVENT_ID, NotificationChannel.EMAIL))
            .willReturn(true);

        notificationService.createWelcomeEmail(COMMAND);

        verify(notificationRepository, never()).save(any());
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }
}
