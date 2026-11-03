package com.bbcc.kidly.boundedcontext.recommend.in.activity;

import com.bbcc.kidly.boundedcontext.recommend.app.activity.ActivityRecordService;
import com.bbcc.kidly.boundedcontext.recommend.in.RecommendKafkaListenerConfig;
import com.bbcc.kidly.shared.search.event.SearchPerformedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.search.events에서 search.performed를 받아 검색 활동 기록 저장을 맡긴다.
 * 같은 메시지를 다시 받으면 서비스가 이미 저장된 이벤트 ID를 보고 건너뛰므로 결과는 한 번 받은 것과 같다.
 * 두 처리가 겹쳐 둘 다 저장하려 하면 이벤트 ID UNIQUE가 하나를 막고, 막힌 쪽은 재시도에서 건너뛴다.
 * 검색어는 회원의 관심사라 로그 · 예외 메시지에 남기지 않는다.
 */
@Component
@RequiredArgsConstructor
public class RecommendSearchEventListener {

    private final ActivityRecordService activityRecordService;
    private final JsonMapper jsonMapper;

    @KafkaListener(id = "recommendSearchEventListener", topics = "kidly.search.events",
        groupId = "kidly.recommend", containerFactory = "recommendKafkaListenerContainerFactory")
    public void on(ConsumerRecord<String, String> record) {
        switch (RecommendKafkaListenerConfig.header(record, "eventType")) {
            case "search.performed" -> activityRecordService.recordSearch(
                jsonMapper.readValue(record.value(), SearchPerformedEvent.class));
            // 헤더가 없으면 어떤 이벤트인지 알 수 없어 재시도 없이 DLT로 보낸다
            case null -> throw new IllegalArgumentException("eventType 헤더가 없습니다");
            // 추천이 받지 않는 종류는 건너뛴다
            default -> { }
        }
    }
}
