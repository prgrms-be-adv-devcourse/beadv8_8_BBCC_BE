package com.bbcc.kidly.boundedcontext.recommend.in.replica;

import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaChildService;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.childprofile.events에서 child.changed를 받아 자녀 복사본 반영을 맡긴다.
 * 같은 메시지를 다시 받아도 복사본이 저장된 시각보다 늦은 변경만 반영하므로 결과는 한 번 받은 것과 같다.
 */
@Component
@RequiredArgsConstructor
public class RecommendChildProfileEventListener {

    private final RecommendReplicaChildService replicaChildService;
    private final JsonMapper jsonMapper;

    @KafkaListener(id = "recommendChildProfileEventListener", topics = "kidly.childprofile.events",
        groupId = "kidly.recommend", containerFactory = "recommendKafkaListenerContainerFactory")
    public void on(ConsumerRecord<String, String> record) {
        switch (RecommendKafkaListenerConfig.header(record, "eventType")) {
            case "child.changed" ->
                replicaChildService.applyChildChanged(jsonMapper.readValue(record.value(), ChildChangedEvent.class));
            default -> { }  // 추천이 받지 않는 종류
        }
    }
}
