package com.bbcc.kidly.boundedcontext.notification.in.event;

import com.bbcc.kidly.boundedcontext.notification.app.MemberContactService;
import com.bbcc.kidly.boundedcontext.notification.app.NotificationService;
import com.bbcc.kidly.boundedcontext.notification.app.dto.WelcomeEmailCommand;
import com.bbcc.kidly.shared.member.event.MemberRegisteredEvent;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.member.events에서 회원 가입 · 탈퇴를 받는다. 이벤트 필드가 바뀌면 여기 변환만 고친다.
 * member.registered: 회원 이메일 복사본을 저장하고 환영 메일을 만든다. 회원 서비스가 기존 회원을 다시 보낸 것(data.resent = true)이면
 * 복사본만 저장한다. resent가 없는 메시지는 처음 가입으로 본다.
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
            case "member.registered" -> onRegistered(record.value());
            case "member.withdrawn" -> onWithdrawn(jsonMapper.readValue(record.value(), MemberWithdrawnEvent.class));
            default -> { }  // 알림이 받지 않는 종류
        }
    }

    private void onRegistered(String value) {
        var event = jsonMapper.readValue(value, MemberRegisteredEvent.class);
        memberContactService.save(event.data().memberId(), event.data().email());
        if (resent(value)) {
            return;
        }
        notificationService.createWelcomeEmail(
            new WelcomeEmailCommand(event.eventId(), event.data().memberId(), event.data().email()));
    }

    // 회원 서비스가 기존 회원 이메일을 채우려고 다시 보낸 가입 이벤트인지. 이 필드는 shared 이벤트에 아직 없어 본문에서 직접 읽는다
    private boolean resent(String value) {
        return jsonMapper.readTree(value).path("data").path("resent").asBoolean(false);
    }

    private void onWithdrawn(MemberWithdrawnEvent event) {
        memberContactService.delete(event.data().memberId());
    }
}
