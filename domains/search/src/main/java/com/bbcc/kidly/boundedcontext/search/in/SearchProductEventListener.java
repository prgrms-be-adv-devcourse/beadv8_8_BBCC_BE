package com.bbcc.kidly.boundedcontext.search.in;

import com.bbcc.kidly.boundedcontext.search.app.SearchIndexService;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.product.events에서 product.changed를 받아 상품 색인 문서를 갱신한다. 다른 종류(재고 차감 등)는 건너뛴다.
 * 같은 메시지를 다시 받거나 순서가 바뀌어 와도 저장된 문서보다 늦게 일어난 변경만 남는다(외부 버전).
 * DB를 쓰지 않으므로 트랜잭션은 열지 않는다.
 */
@Component
@RequiredArgsConstructor
public class SearchProductEventListener {

    private final SearchIndexService indexService;
    private final JsonMapper jsonMapper;

    @KafkaListener(id = "searchProductEventListener", topics = SearchKafkaListenerConfig.PRODUCT_EVENTS,
        groupId = SearchKafkaListenerConfig.GROUP, containerFactory = "searchKafkaListenerContainerFactory")
    public void on(ConsumerRecord<String, String> record) {
        switch (SearchKafkaListenerConfig.eventType(record)) {
            case "product.changed" ->
                indexService.applyProductChanged(jsonMapper.readValue(record.value(), ProductChangedEvent.class));
            default -> { }  // 검색이 받지 않는 종류
        }
    }
}
