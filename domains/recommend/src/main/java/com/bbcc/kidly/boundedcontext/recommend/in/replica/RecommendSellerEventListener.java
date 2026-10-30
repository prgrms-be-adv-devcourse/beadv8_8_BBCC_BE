package com.bbcc.kidly.boundedcontext.recommend.in.replica;

import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaSellerService;
import com.bbcc.kidly.boundedcontext.recommend.in.RecommendKafkaListenerConfig;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.seller.events에서 seller.changed를 받아 판매자 복사본 반영을 맡긴다.
 * 회원에 {@code SellerEventListener}가 있어서, 스프링 빈 이름이 겹치지 않게 클래스 이름 앞에 도메인 이름을 붙인다.
 * 같은 메시지를 다시 받아도 복사본이 저장된 시각보다 늦은 변경만 반영하므로 결과는 한 번 받은 것과 같다.
 */
@Component
@RequiredArgsConstructor
public class RecommendSellerEventListener {

    private final RecommendReplicaSellerService replicaSellerService;
    private final JsonMapper jsonMapper;

    @KafkaListener(id = "recommendSellerEventListener", topics = "kidly.seller.events",
        groupId = "kidly.recommend", containerFactory = "recommendKafkaListenerContainerFactory")
    public void on(ConsumerRecord<String, String> record) {
        switch (RecommendKafkaListenerConfig.header(record, "eventType")) {
            case "seller.changed" ->
                replicaSellerService.applySellerChanged(jsonMapper.readValue(record.value(), SellerChangedEvent.class));
            // 헤더가 없으면 어떤 이벤트인지 알 수 없어 재시도 없이 DLT로 보낸다
            case null -> throw new IllegalArgumentException("eventType 헤더가 없습니다");
            // 추천이 받지 않는 종류는 건너뛴다
            default -> { }
        }
    }
}
