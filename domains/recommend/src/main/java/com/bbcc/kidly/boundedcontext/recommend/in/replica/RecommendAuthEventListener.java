package com.bbcc.kidly.boundedcontext.recommend.in.replica;

import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaChildAccountService;
import com.bbcc.kidly.boundedcontext.recommend.in.RecommendKafkaListenerConfig;
import com.bbcc.kidly.shared.auth.event.ChildAccountChangedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.auth.events에서 child-account.changed를 받아 자녀 계정 복사본 반영을 맡긴다. 토큰 무효화(auth.tokens-revoked)는 받지 않는다.
 * 추천 그룹은 이 토픽을 처음 읽으므로 책갈피가 없다. 앱 기본값(latest)이면 구독 전에 만든 계정을 모르므로 이 리스너만 처음부터 읽는다.
 * 같은 메시지를 다시 받아도 결과가 같다(RecommendReplicaChildAccount).
 */
@Component
@RequiredArgsConstructor
public class RecommendAuthEventListener {

    private final RecommendReplicaChildAccountService childAccountService;
    private final JsonMapper jsonMapper;

    @KafkaListener(id = "recommendAuthEventListener", topics = "kidly.auth.events",
        groupId = "kidly.recommend", containerFactory = "recommendKafkaListenerContainerFactory",
        properties = "auto.offset.reset=earliest")
    public void on(ConsumerRecord<String, String> record) {
        switch (RecommendKafkaListenerConfig.header(record, "eventType")) {
            case "child-account.changed" -> childAccountService.applyChildAccountChanged(
                jsonMapper.readValue(record.value(), ChildAccountChangedEvent.class));
            // 헤더가 없으면 어떤 이벤트인지 알 수 없어 재시도 없이 DLT로 보낸다
            case null -> throw new IllegalArgumentException("eventType 헤더가 없습니다");
            // 추천이 받지 않는 종류(토큰 무효화)는 건너뛴다
            default -> { }
        }
    }
}
