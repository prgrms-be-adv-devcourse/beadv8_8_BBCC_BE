package com.bbcc.kidly.boundedcontext.recommend.in.purchase;

import com.bbcc.kidly.boundedcontext.recommend.app.purchase.PurchaseRecordService;
import com.bbcc.kidly.boundedcontext.recommend.in.RecommendKafkaListenerConfig;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.order.events에서 order-item.purchase-confirmed를 받아 구매 기록 저장을 맡긴다.
 * 같은 주문상품의 구매확정을 다시 받아도 구매 기록은 한 줄만 남으므로 결과는 한 번 받은 것과 같다.
 */
@Component
@RequiredArgsConstructor
public class RecommendOrderEventListener {

    private final PurchaseRecordService purchaseRecordService;
    private final JsonMapper jsonMapper;

    @KafkaListener(id = "recommendOrderEventListener", topics = "kidly.order.events",
        groupId = "kidly.recommend", containerFactory = "recommendKafkaListenerContainerFactory")
    public void on(ConsumerRecord<String, String> record) {
        switch (RecommendKafkaListenerConfig.header(record, "eventType")) {
            case "order-item.purchase-confirmed" -> purchaseRecordService.recordPurchaseConfirmed(
                jsonMapper.readValue(record.value(), OrderItemPurchaseConfirmedEvent.class));
            // 헤더가 없으면 어떤 이벤트인지 알 수 없어 재시도 없이 DLT로 보낸다
            case null -> throw new IllegalArgumentException("eventType 헤더가 없습니다");
            // 추천이 받지 않는 종류는 건너뛴다
            default -> { }
        }
    }
}
