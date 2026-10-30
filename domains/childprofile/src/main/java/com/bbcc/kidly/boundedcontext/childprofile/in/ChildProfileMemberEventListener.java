package com.bbcc.kidly.boundedcontext.childprofile.in;

import com.bbcc.kidly.boundedcontext.childprofile.app.ChildProfileService;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.member.events에서 member.withdrawn을 받아 그 회원의 자녀 프로필을 모두 삭제한다.
 * 같은 메시지를 다시 받아도 이미 삭제된 자녀는 조회되지 않아 아무 일도 하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ChildProfileMemberEventListener {

    private final ChildProfileService childProfileService;
    private final JsonMapper jsonMapper;

    @KafkaListener(id = "childProfileMemberEventListener", topics = "kidly.member.events",
        groupId = "kidly.childprofile", containerFactory = "childprofileKafkaListenerContainerFactory")
    @Transactional
    public void on(ConsumerRecord<String, String> record) {
        switch (ChildProfileKafkaListenerConfig.header(record, "eventType")) {
            case "member.withdrawn" -> onWithdrawn(jsonMapper.readValue(record.value(), MemberWithdrawnEvent.class));
            default -> { }  // 자녀 프로필이 받지 않는 종류
        }
    }

    private void onWithdrawn(MemberWithdrawnEvent event) {
        childProfileService.deleteAll(event.data().memberId());
    }
}
