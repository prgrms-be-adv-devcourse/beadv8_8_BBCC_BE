package com.bbcc.kidly.boundedcontext.member.in;

import com.bbcc.kidly.boundedcontext.member.app.ShippingAddressService;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class ShippingAddressMemberEventListener {

    private final ShippingAddressService shippingAddressService;
    private final JsonMapper jsonMapper;

    @KafkaListener(id = "memberMemberEventListener", topics = MemberKafkaListenerConfig.MEMBER_EVENTS,
        groupId = "kidly.member", containerFactory = "memberKafkaListenerContainerFactory")
    public void on(ConsumerRecord<String, String> record) {
        switch (MemberKafkaListenerConfig.header(record, "eventType")) {
            case "member.withdrawn" -> onWithdrawn(jsonMapper.readValue(record.value(), MemberWithdrawnEvent.class));
            default -> { }  // 회원이 받지 않는 종류
        }
    }

    private void onWithdrawn(MemberWithdrawnEvent event) {
        shippingAddressService.deleteAll(event.data().memberId());
    }
}
