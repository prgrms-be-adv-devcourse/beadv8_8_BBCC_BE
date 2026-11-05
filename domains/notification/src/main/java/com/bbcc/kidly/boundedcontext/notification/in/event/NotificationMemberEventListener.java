package com.bbcc.kidly.boundedcontext.notification.in.event;

import com.bbcc.kidly.boundedcontext.notification.app.MemberContactService;
import com.bbcc.kidly.boundedcontext.notification.app.NotificationService;
import com.bbcc.kidly.boundedcontext.notification.app.dto.WelcomeEmailCommand;
import com.bbcc.kidly.shared.member.event.MemberRegisteredEvent;
import com.bbcc.kidly.shared.member.event.MemberSuspendedEvent;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.member.events에서 회원 가입 · 탈퇴를 받는다. 이벤트 필드가 바뀌면 여기 변환만 고친다.
 * member.registered: 회원 이메일 복사본을 저장하고 환영 메일을 만든다.
 * member.suspended: 정지 사유 메일을 만든다.
 * member.withdrawn: 복사본을 지운다.
 * 같은 메시지를 다시 받아도 복사본은 한 건이고, 환영 메일은 NotificationService가 eventId로 한 통만 만든다.
 */
@Component
@RequiredArgsConstructor
public class NotificationMemberEventListener {

    private final NotificationService notificationService;
    private final MemberContactService memberContactService;
    private final JsonMapper jsonMapper;

    @KafkaListener(id = "notificationMemberEventListener", topics = "kidly.member.events",
        groupId = "kidly.notification", containerFactory = "notificationKafkaListenerContainerFactory")
    @Transactional
    public void on(ConsumerRecord<String, String> record) {
        switch (NotificationKafkaListenerConfig.header(record, "eventType")) {
            case "member.registered" -> onRegistered(jsonMapper.readValue(record.value(), MemberRegisteredEvent.class));
            case "member.suspended" -> onSuspended(jsonMapper.readValue(record.value(), MemberSuspendedEvent.class));
            case "member.withdrawn" -> onWithdrawn(jsonMapper.readValue(record.value(), MemberWithdrawnEvent.class));
            default -> { }  // 알림이 받지 않는 종류
        }
    }

    private void onRegistered(MemberRegisteredEvent event) {
        memberContactService.save(event.data().memberId(), event.data().email());
        notificationService.createWelcomeEmail(
            new WelcomeEmailCommand(event.eventId(), event.data().memberId(), event.data().email()));
    }

    private void onSuspended(MemberSuspendedEvent event) {
        notificationService.createSuspendedEmail(event.eventId(), event.data().memberId(), event.data().reason());
    }

    private void onWithdrawn(MemberWithdrawnEvent event) {
        memberContactService.delete(event.data().memberId());
    }
}
