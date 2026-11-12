package com.bbcc.kidly.boundedcontext.search.in;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 검색이 색인하지 못해 DLT로 보낸 상품 · 판매자 변경을 다시 읽어 색인에 반영한다(#1026).
 * Elasticsearch가 잠깐 멈춘 동안 받은 변경이 DLT로 가도, 복구되면 손대지 않아도 검색에 들어가게 하려는 것이다.
 * 원래 토픽에 다시 넣지 않고 원래 리스너와 같은 처리를 부른다. DLT는 원래 메시지의 헤더 · 키 · 본문을 그대로 담는다.
 * 같은 변경을 여러 번 반영해도 외부 버전(occurredAt)으로 늦게 일어난 변경만 남는다.
 * 실패하면 될 때까지 다시 시도하고, 다시 해도 같은 실패면 건너뛴다(SearchKafkaListenerConfig).
 * 다시 시도하는 동안 이 리스너만 멈추고 원래 토픽을 받는 리스너는 따로 돈다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchDeadLetterListener {

    private final SearchProductEventListener productListener;
    private final SearchSellerEventListener sellerListener;

    @KafkaListener(id = "searchDeadLetterListener",
        topics = {SearchKafkaListenerConfig.PRODUCT_DEAD_LETTERS, SearchKafkaListenerConfig.SELLER_DEAD_LETTERS},
        groupId = SearchKafkaListenerConfig.GROUP, containerFactory = "searchKafkaListenerContainerFactory")
    public void on(ConsumerRecord<String, String> record) {
        if (SearchKafkaListenerConfig.PRODUCT_DEAD_LETTERS.equals(record.topic())) {
            productListener.on(record);
        } else {
            sellerListener.on(record);
        }
        log.info("DLT 이벤트를 색인에 반영했습니다. topic={} eventType={} eventId={} key={}", record.topic(),
            SearchKafkaListenerConfig.header(record, "eventType"), SearchKafkaListenerConfig.header(record, "eventId"),
            record.key());
    }
}
