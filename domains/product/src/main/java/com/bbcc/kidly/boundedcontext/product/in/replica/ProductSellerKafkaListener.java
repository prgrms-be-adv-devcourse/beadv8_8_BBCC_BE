package com.bbcc.kidly.boundedcontext.product.in.replica;

import com.bbcc.kidly.boundedcontext.product.app.replica.ProductReplicaSellerService;
import com.bbcc.kidly.boundedcontext.product.in.ProductKafkaListenerConfig;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

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
            case null -> throw new IllegalArgumentException("eventType 헤더가 없습니다");
            default -> { }
        }
    }
}
