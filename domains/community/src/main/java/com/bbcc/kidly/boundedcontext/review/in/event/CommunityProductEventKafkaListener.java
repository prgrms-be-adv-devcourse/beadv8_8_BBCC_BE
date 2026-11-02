package com.bbcc.kidly.boundedcontext.review.in.event;

import com.bbcc.kidly.boundedcontext.review.in.config.CommunityKafkaListenerConfig;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.product.events에서 product.changed를 받아 Spring 수신과 같은 처리를 부른다. 다른 종류(재고 등)는 건너뛴다.
 * <ul>
 *   <li>처리는 처리 메서드가 여는 트랜잭션에서 커밋까지 끝난다. 실패하면 예외를 그대로 던진다(재시도 → DLT)</li>
 *   <li>모르는 enum 값(상품 · 옵션 판매 상태)은 null로 읽는다: 상품 상태를 모르면 판매 종료로 보인다.
 *       그 밖에 본문을 읽지 못하면(eventType 헤더 없음 · 깨진 JSON) 재시도 없이 DLT로 간다(CommunityKafkaListenerConfig)</li>
 * </ul>
 */
@Component
public class CommunityProductEventKafkaListener {

    public static final String ID = "communityProductEventKafkaListener";
    private static final String PRODUCT_CHANGED = "product.changed";

    private final ObjectReader productChangedReader;
    private final CommunityProductEventListener productEventListener;

    public CommunityProductEventKafkaListener(JsonMapper jsonMapper,
        CommunityProductEventListener productEventListener) {
        this.productChangedReader = jsonMapper.readerFor(ProductChangedEvent.class)
            .with(EnumFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL);
        this.productEventListener = productEventListener;
    }

    @KafkaListener(id = ID, topics = CommunityKafkaListenerConfig.PRODUCT_EVENTS,
        groupId = CommunityKafkaListenerConfig.GROUP, containerFactory = CommunityKafkaListenerConfig.FACTORY)
    public void on(ConsumerRecord<String, String> record) {
        if (PRODUCT_CHANGED.equals(CommunityKafkaMessages.eventType(record))) {
            productEventListener.onProductChanged(productChangedReader.readValue(record.value()));
        }
    }
}
