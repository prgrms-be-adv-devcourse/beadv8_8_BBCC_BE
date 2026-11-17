package com.bbcc.kidly.boundedcontext.auth.in;

import com.bbcc.kidly.boundedcontext.auth.app.SocialLoginService;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class MemberWithdrawnEventListener {

    private final SocialLoginService socialLoginService;
    private final JsonMapper jsonMapper;

    @KafkaListener(id = "authMemberEventListener", topics = AuthKafkaListenerConfig.MEMBER_EVENTS,
        groupId = "kidly.auth", containerFactory = "authKafkaListenerContainerFactory")
    public void on(ConsumerRecord<String, String> record) {
        switch (AuthKafkaListenerConfig.header(record, "eventType")) {
            case "member.withdrawn" -> onWithdrawn(jsonMapper.readValue(record.value(), MemberWithdrawnEvent.class));
            default -> { }
        }
    }

    private void onWithdrawn(MemberWithdrawnEvent event) {
        socialLoginService.unlinkAll(event.data().memberId());
    }
}
