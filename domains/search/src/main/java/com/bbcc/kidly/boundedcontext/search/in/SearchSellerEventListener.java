package com.bbcc.kidly.boundedcontext.search.in;

import com.bbcc.kidly.boundedcontext.search.app.SearchIndexService;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.seller.events에서 seller.changed를 받아 판매자 문서(상호명)를 갱신한다. 다른 종류는 건너뛴다.
 * 같은 메시지를 다시 받거나 순서가 바뀌어 와도 저장된 값보다 늦게 일어난 변경만 남는다(외부 버전).
 */
@Component
@RequiredArgsConstructor
public class SearchSellerEventListener {

    private final SearchIndexService indexService;
    private final JsonMapper jsonMapper;

    @KafkaListener(id = "searchSellerEventListener", topics = SearchKafkaListenerConfig.SELLER_EVENTS,
        groupId = SearchKafkaListenerConfig.GROUP, containerFactory = "searchKafkaListenerContainerFactory")
    public void on(ConsumerRecord<String, String> record) {
        switch (SearchKafkaListenerConfig.eventType(record)) {
            case "seller.changed" ->
                indexService.applySellerChanged(jsonMapper.readValue(record.value(), SellerChangedEvent.class));
            default -> { }  // 검색이 받지 않는 종류
        }
    }
}
