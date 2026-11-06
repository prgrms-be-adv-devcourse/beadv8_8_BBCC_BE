package com.bbcc.kidly.boundedcontext.childprofile.in;

import com.bbcc.kidly.boundedcontext.childprofile.app.ChildProfileService;
import com.bbcc.kidly.shared.member.event.MemberSuspendedEvent;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class ChildProfileMemberEventListener {

    private final ChildProfileService childProfileService;
    private final JsonMapper jsonMapper;

    @KafkaListener(id = "childProfileMemberEventListener", topics = "kidly.member.events",
        groupId = "kidly.childprofile", containerFactory = "childprofileKafkaListenerContainerFactory")
    public void on(ConsumerRecord<String, String> record) {
        switch (ChildProfileKafkaListenerConfig.header(record, "eventType")) {
            case "member.withdrawn" -> onWithdrawn(jsonMapper.readValue(record.value(), MemberWithdrawnEvent.class));
            case "member.suspended" -> onSuspended(jsonMapper.readValue(record.value(), MemberSuspendedEvent.class));
            default -> { }
        }
    }

    private void onWithdrawn(MemberWithdrawnEvent event) {
        childProfileService.deleteWithdrawnMember(event.data().memberId());
    }

    private void onSuspended(MemberSuspendedEvent event) {
        childProfileService.deleteAll(event.data().memberId());
    }
}
