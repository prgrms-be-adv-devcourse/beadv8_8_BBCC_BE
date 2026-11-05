package com.bbcc.kidly.boundedcontext.recommend.in.activity;

import com.bbcc.kidly.boundedcontext.recommend.app.activity.ActivityRecordService;
import com.bbcc.kidly.boundedcontext.recommend.in.RecommendKafkaListenerConfig;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.member.events에서 member.withdrawn을 받아 탈퇴한 회원의 활동 기록 삭제를 맡긴다.
 * 같은 탈퇴를 다시 받아도 이미 지워진 기록은 없으므로 결과는 한 번 받은 것과 같다.
 */
@Component
@RequiredArgsConstructor
public class RecommendMemberEventListener {

    private final ActivityRecordService activityRecordService;
    private final JsonMapper jsonMapper;

    @KafkaListener(id = "recommendMemberEventListener", topics = "kidly.member.events",
        groupId = "kidly.recommend", containerFactory = "recommendKafkaListenerContainerFactory")
    public void on(ConsumerRecord<String, String> record) {
        switch (RecommendKafkaListenerConfig.header(record, "eventType")) {
            case "member.withdrawn" -> activityRecordService.deleteWithdrawnMemberActivities(
                jsonMapper.readValue(record.value(), MemberWithdrawnEvent.class));
            // 헤더가 없으면 어떤 이벤트인지 알 수 없어 재시도 없이 DLT로 보낸다
            case null -> throw new IllegalArgumentException("eventType 헤더가 없습니다");
            // 추천이 받지 않는 종류는 건너뛴다
            default -> { }
        }
    }
}
