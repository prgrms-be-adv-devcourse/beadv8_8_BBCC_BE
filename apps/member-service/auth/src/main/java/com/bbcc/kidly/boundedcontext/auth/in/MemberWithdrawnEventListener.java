package com.bbcc.kidly.boundedcontext.auth.in;

import com.bbcc.kidly.boundedcontext.auth.app.SocialLoginService;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.member.events에서 member.withdrawn을 받아 그 회원의 소셜 계정 연결을 모두 지운다.
 * 같은 메시지를 다시 받아도 지울 연결이 없어 아무 일도 하지 않는다. 트랜잭션은 SocialLoginService.unlinkAll이 연다.
 */
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
            default -> { }  // 인증이 받지 않는 종류
        }
    }

    private void onWithdrawn(MemberWithdrawnEvent event) {
        socialLoginService.unlinkAll(event.data().memberId());
    }
}
