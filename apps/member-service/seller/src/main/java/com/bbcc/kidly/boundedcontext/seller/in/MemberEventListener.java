package com.bbcc.kidly.boundedcontext.seller.in;

import com.bbcc.kidly.boundedcontext.seller.app.SellerApplicationService;
import com.bbcc.kidly.shared.member.event.MemberChangedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.member.events에서 member.changed를 받아 그 회원의 판매자 신청에 반영한다.
 * 같은 메시지를 다시 받아도 신청이 저장된 변경 시각보다 늦은 변경만 반영하므로 결과는 한 번 받은 것과 같다.
 */
@Component
@RequiredArgsConstructor
public class MemberEventListener {

    private final SellerApplicationService sellerApplicationService;
    private final JsonMapper jsonMapper;

    @KafkaListener(id = "sellerMemberEventListener", topics = "kidly.member.events",
        groupId = "kidly.seller", containerFactory = "sellerKafkaListenerContainerFactory")
    @Transactional
    public void on(ConsumerRecord<String, String> record) {
        switch (SellerKafkaListenerConfig.header(record, "eventType")) {
            case "member.changed" -> onChanged(jsonMapper.readValue(record.value(), MemberChangedEvent.class));
            default -> { }  // 판매자가 받지 않는 종류
        }
    }

    private void onChanged(MemberChangedEvent event) {
        var data = event.data();
        sellerApplicationService.changeApplicantNickname(data.memberId(), data.nickname(), event.occurredAt());
    }
}
