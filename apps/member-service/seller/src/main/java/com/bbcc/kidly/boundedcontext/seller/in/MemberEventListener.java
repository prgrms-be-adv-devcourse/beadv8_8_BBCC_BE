package com.bbcc.kidly.boundedcontext.seller.in;

import com.bbcc.kidly.boundedcontext.seller.app.SellerApplicationService;
import com.bbcc.kidly.boundedcontext.seller.app.SellerService;
import com.bbcc.kidly.shared.member.event.MemberChangedEvent;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class MemberEventListener {

    private final SellerApplicationService sellerApplicationService;
    private final SellerService sellerService;
    private final JsonMapper jsonMapper;

    @KafkaListener(id = "sellerMemberEventListener", topics = SellerKafkaListenerConfig.MEMBER_EVENTS,
        groupId = "kidly.seller", containerFactory = "sellerKafkaListenerContainerFactory")
    @Transactional
    public void on(ConsumerRecord<String, String> record) {
        switch (SellerKafkaListenerConfig.header(record, "eventType")) {
            case "member.changed" -> onChanged(jsonMapper.readValue(record.value(), MemberChangedEvent.class));
            case "member.withdrawn" -> onWithdrawn(jsonMapper.readValue(record.value(), MemberWithdrawnEvent.class));
            default -> { }
        }
    }

    private void onChanged(MemberChangedEvent event) {
        var data = event.data();
        sellerApplicationService.changeApplicantNickname(data.memberId(), data.nickname(), event.occurredAt());
    }

    private void onWithdrawn(MemberWithdrawnEvent event) {
        sellerApplicationService.clearApplicantPersonalInfo(event.data().memberId(), event.occurredAt());
        sellerService.closeIfOpen(event.data().memberId());
    }
}
