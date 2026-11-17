package com.bbcc.kidly.boundedcontext.member.in;

import com.bbcc.kidly.boundedcontext.member.app.SellerStateService;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class SellerEventListener {

    private final SellerStateService sellerStateService;
    private final JsonMapper jsonMapper;

    @KafkaListener(id = "memberSellerEventListener", topics = MemberKafkaListenerConfig.SELLER_EVENTS,
        groupId = "kidly.member", containerFactory = "memberKafkaListenerContainerFactory")
    public void on(ConsumerRecord<String, String> record) {
        switch (MemberKafkaListenerConfig.header(record, "eventType")) {
            case "seller.changed" -> onChanged(jsonMapper.readValue(record.value(), SellerChangedEvent.class));
            default -> { }  // 회원이 받지 않는 종류
        }
    }

    private void onChanged(SellerChangedEvent event) {
        var data = event.data();
        sellerStateService.apply(data.sellerId(), data.status(), event.occurredAt());
    }
}
