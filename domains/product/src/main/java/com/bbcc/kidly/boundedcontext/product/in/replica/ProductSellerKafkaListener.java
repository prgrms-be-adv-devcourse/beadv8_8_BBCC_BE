package com.bbcc.kidly.boundedcontext.product.in.replica;

import com.bbcc.kidly.boundedcontext.product.app.replica.ProductReplicaSellerService;
import com.bbcc.kidly.boundedcontext.product.in.ProductKafkaListenerConfig;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.seller.events에서 seller.changed를 받아 판매자 복사본 반영을 맡긴다.
 * 같은 메시지를 다시 받아도 복사본이 저장된 시각보다 늦은 변경만 반영하므로 결과는 한 번 받은 것과 같다.
 * 트랜잭션은 서비스가 연다.
 */
@Component
@RequiredArgsConstructor
public class ProductSellerKafkaListener {

    public static final String ID = "productSellerKafkaListener";

    private final ProductReplicaSellerService replicaSellerService;
    private final JsonMapper jsonMapper;

    @KafkaListener(id = ID, topics = ProductKafkaListenerConfig.SELLER_EVENTS,
        groupId = ProductKafkaListenerConfig.GROUP, containerFactory = "productKafkaListenerContainerFactory",
        autoStartup = ProductKafkaListenerConfig.AUTO_STARTUP)
    public void on(ConsumerRecord<String, String> record) {
        switch (ProductKafkaListenerConfig.header(record, "eventType")) {
            case "seller.changed" ->
                replicaSellerService.applySellerChanged(jsonMapper.readValue(record.value(), SellerChangedEvent.class));
            // 헤더가 없으면 어떤 이벤트인지 알 수 없어 재시도 없이 DLT로 보낸다
            case null -> throw new IllegalArgumentException("eventType 헤더가 없습니다");
            // 상품이 받지 않는 종류는 건너뛴다
            default -> { }
        }
    }
}
