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

/**
 * kidly.member.events에서 member.changed · member.withdrawn을 받아 그 회원의 판매자 신청에 반영한다.
 * member.changed는 신청이 저장된 변경 시각보다 늦은 변경만 반영하고, member.withdrawn은 같은 칸을 null로 비우고 같은 탈퇴 시각을 남기므로
 * 같은 메시지를 다시 받아도 결과는 한 번 받은 것과 같다.
 */
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
            default -> { }  // 판매자가 받지 않는 종류
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
