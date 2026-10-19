package com.bbcc.kidly.boundedcontext.notification.in.event;

import com.bbcc.kidly.boundedcontext.notification.app.NotificationService;
import com.bbcc.kidly.boundedcontext.notification.app.dto.WelcomeEmailCommand;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.member.event.MemberRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * member.registered를 받아 환영 메일을 만든다. 이벤트 필드가 바뀌면 여기 변환만 고친다.
 */
@Component
@RequiredArgsConstructor
public class NotificationMemberEventListener {

    private final NotificationService notificationService;

    @DomainEventListener
    public void onRegistered(MemberRegisteredEvent event) {
        notificationService.createWelcomeEmail(
            new WelcomeEmailCommand(event.eventId(), event.data().memberId(), event.data().email()));
    }
}
