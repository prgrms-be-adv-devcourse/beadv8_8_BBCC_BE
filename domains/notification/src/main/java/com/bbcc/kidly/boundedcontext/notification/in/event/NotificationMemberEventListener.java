package com.bbcc.kidly.boundedcontext.notification.in.event;

import com.bbcc.kidly.boundedcontext.notification.app.MemberContactService;
import com.bbcc.kidly.boundedcontext.notification.app.MemberWithdrawalService;
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
 * 관리자가 기존 회원을 다시 보낸 것(resent)은 복사본만 저장한다.
 * 이미 탈퇴한 회원(DLT 재처리로 탈퇴 뒤에 처리되는 경우)이면 아무것도 하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class NotificationMemberEventListener {

    private final NotificationService notificationService;
    private final MemberContactService memberContactService;
    private final MemberWithdrawalService memberWithdrawalService;
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
        if (memberWithdrawalService.lockAndCheckWithdrawn(event.data().memberId())) {
            return;
        }
        memberContactService.save(event.data().memberId(), event.data().email());
        if (Boolean.TRUE.equals(event.data().resent())) {  // 없는 예전 메시지(null)는 새 가입
            return;
        }
        notificationService.createWelcomeEmail(
            new WelcomeEmailCommand(event.eventId(), event.data().memberId(), event.data().email()));
    }

    private void onSuspended(MemberSuspendedEvent event) {
        notificationService.createSuspendedEmail(event.eventId(), event.data().memberId(), event.data().reason());
    }

    private void onWithdrawn(MemberWithdrawnEvent event) {
        memberWithdrawalService.purge(event.data().memberId());
    }
}
