package com.bbcc.kidly.boundedcontext.member.in;

import com.bbcc.kidly.boundedcontext.member.app.SellerStateService;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.seller.events에서 seller.changed를 받아 판매자 상태 복사본을 갱신한다(#608). SELLER를 잃으면 Access Token을 무효화한다.
 * 같은 메시지를 다시 받아도 저장된 변경 시각보다 늦은 변경만 반영하고, 이미 SELLER가 없으면 다시 무효화하지 않으므로 결과는 한 번 받은 것과 같다.
 * 트랜잭션은 SellerStateService.apply가 연다.
 */
@Component
@RequiredArgsConstructor
public class SellerEventListener {

    private final SellerStateService sellerStateService;
    private final JsonMapper jsonMapper;

    @KafkaListener(id = "memberSellerEventListener", topics = "kidly.seller.events",
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
