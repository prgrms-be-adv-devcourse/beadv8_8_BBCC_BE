package com.bbcc.kidly.boundedcontext.notification.in.event;

import com.bbcc.kidly.boundedcontext.notification.app.NotificationService;
import com.bbcc.kidly.boundedcontext.notification.app.dto.WelcomeEmailCommand;
import com.bbcc.kidly.shared.member.event.MemberRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.member.events에서 member.registered를 받아 환영 메일을 만든다. 이벤트 필드가 바뀌면 여기 변환만 고친다.
 * 같은 메시지를 다시 받아도 NotificationService가 eventId로 한 통만 만든다.
 */
@Component
@RequiredArgsConstructor
public class NotificationMemberEventListener {

    private final NotificationService notificationService;
    private final JsonMapper jsonMapper;

    @KafkaListener(id = "notificationMemberEventListener", topics = "kidly.member.events",
        groupId = "kidly.notification", containerFactory = "notificationKafkaListenerContainerFactory")
    @Transactional
    public void on(ConsumerRecord<String, String> record) {
        switch (NotificationKafkaListenerConfig.header(record, "eventType")) {
            case "member.registered" -> onRegistered(jsonMapper.readValue(record.value(), MemberRegisteredEvent.class));
            default -> { }  // 알림이 받지 않는 종류
        }
    }

    private void onRegistered(MemberRegisteredEvent event) {
        notificationService.createWelcomeEmail(
            new WelcomeEmailCommand(event.eventId(), event.data().memberId(), event.data().email()));
    }
}
