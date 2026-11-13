package com.bbcc.kidly.boundedcontext.recommend.in.activity;

import com.bbcc.kidly.boundedcontext.recommend.app.activity.ActivityRecordService;
import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaReviewService;
import com.bbcc.kidly.boundedcontext.recommend.in.RecommendKafkaListenerConfig;
import com.bbcc.kidly.shared.community.event.FavoriteAddedEvent;
import com.bbcc.kidly.shared.community.event.FavoriteRemovedEvent;
import com.bbcc.kidly.shared.community.event.ReviewChangedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.community.events에서 favorite.added · favorite.removed를 받아 찜 · 찜 해제 활동 기록 저장을,
 * review.changed를 받아 리뷰 복사본 반영을 맡긴다.
 * 같은 토픽 · 같은 그룹에 리스너 클래스가 둘이면 파티션을 나눠 가져 각자 일부만 받으므로, 커뮤니티 토픽은 이 클래스 하나가 받는다.
 * 같은 메시지를 다시 받으면 찜은 서비스가 이미 저장된 이벤트 ID를 보고 건너뛰고, 리뷰는 저장된 시각보다 늦은 변경만 반영하므로
 * 결과는 한 번 받은 것과 같다.
 * 찜한 상품은 회원의 관심사라 로그 · 예외 메시지에 본문을 남기지 않는다.
 */
@Component
@RequiredArgsConstructor
public class RecommendCommunityEventListener {

    private final ActivityRecordService activityRecordService;
    private final RecommendReplicaReviewService replicaReviewService;
    private final JsonMapper jsonMapper;

    @KafkaListener(id = "recommendCommunityEventListener", topics = "kidly.community.events",
        groupId = "kidly.recommend", containerFactory = "recommendKafkaListenerContainerFactory")
    public void on(ConsumerRecord<String, String> record) {
        switch (RecommendKafkaListenerConfig.header(record, "eventType")) {
            case "favorite.added" -> activityRecordService.recordFavoriteAdded(
                jsonMapper.readValue(record.value(), FavoriteAddedEvent.class));
            case "favorite.removed" -> activityRecordService.recordFavoriteRemoved(
                jsonMapper.readValue(record.value(), FavoriteRemovedEvent.class));
            case "review.changed" -> replicaReviewService.applyReviewChanged(
                jsonMapper.readValue(record.value(), ReviewChangedEvent.class));
            // 헤더가 없으면 어떤 이벤트인지 알 수 없어 재시도 없이 DLT로 보낸다
            case null -> throw new IllegalArgumentException("eventType 헤더가 없습니다");
            // 추천이 받지 않는 종류는 건너뛴다
            default -> { }
        }
    }
}
