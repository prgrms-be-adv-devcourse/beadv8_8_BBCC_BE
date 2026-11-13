package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendKafkaMessages.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityType;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaReview;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaReviewRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.ReviewFit;
import com.bbcc.kidly.boundedcontext.recommend.out.activity.JpaActivityRecordRepository;
import com.bbcc.kidly.shared.community.event.FavoriteAddedEvent;
import com.bbcc.kidly.shared.community.event.FavoriteRemovalCause;
import com.bbcc.kidly.shared.community.event.FavoriteRemovedEvent;
import com.bbcc.kidly.shared.community.event.ReviewChangedEvent;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 추천이 커뮤니티 토픽의 찜 · 찜 해제를 받아 활동 기록에, 리뷰 변경됨을 받아 리뷰 복사본에 저장하는지 확인한다(#1316).
 * 메시지는 팀 Kafka 규칙의 형식으로 토픽에 직접 넣는다. 커뮤니티는 다른 앱이라 이 테스트에서 띄우지 않는다.
 * 같은 토픽에는 다른 테스트의 메시지도 쌓여 이어서 받을 수 있으므로, 회원 ID(찜 이벤트의 키)와 리뷰 ID(리뷰 이벤트의 키)를 테스트마다 새로 만든다.
 */
@KafkaIntegrationTest
class RecommendCommunityKafkaIntegrationTest {

    private static final String COMMUNITY_TOPIC = "kidly.community.events";
    private static final String COMMUNITY_DLT = "kidly.community.events.recommend.dlt";
    private static final LocalDateTime FAVORITED_AT = LocalDateTime.of(2026, 11, 3, 10, 4);
    private static final Long PRODUCT_ID = 31L;
    private static final LocalDateTime REVIEWED_AT = LocalDateTime.of(2026, 11, 5, 9, 0);

    @Autowired
    private JpaActivityRecordRepository activityRecordRepository;

    @Autowired
    private RecommendReplicaReviewRepository replicaReviewRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Autowired
    private KafkaContainer kafka;

    private RecommendKafkaMessages messages;

    private List<MessageListenerContainer> listeners;

    // 리스너는 이 테스트 안에서만 켠다
    @BeforeEach
    void startListeners() {
        messages = new RecommendKafkaMessages(kafka.getBootstrapServers(), jsonMapper);
        listeners = RecommendKafkaMessages.startListeners(kafkaListenerEndpointRegistry);
    }

    // stop()은 처리 중인 메시지가 끝날 때까지 기다리므로 뒤이은 테이블 비우기와 겹치지 않는다
    @AfterEach
    void stopListeners() {
        listeners.forEach(MessageListenerContainer::stop);
    }

    @Test
    @DisplayName("PREF-20 찜을 커뮤니티 토픽에서 받아 회원의 찜 활동 기록으로 저장한다")
    void saveFavoriteAdded() throws Exception {
        Long memberId = newId();
        var event = favoriteAdded(memberId);

        messages.send(COMMUNITY_TOPIC, event);

        AsyncWait.until(TIMEOUT, () -> !recordsOf(memberId).isEmpty());
        assertThat(recordsOf(memberId)).singleElement()
            .extracting(ActivityRecord::getType, ActivityRecord::getEventId, ActivityRecord::getFavoriteId,
                ActivityRecord::getProductId, ActivityRecord::getOccurredAt)
            .containsExactly(ActivityType.FAVORITE_ADDED, event.eventId(), 401L, PRODUCT_ID, FAVORITED_AT);
    }

    @Test
    @DisplayName("PREF-21 회원이 직접 해제한 찜을 커뮤니티 토픽에서 받아 그 찜의 해제 기록으로 저장한다")
    void saveFavoriteRemovedByUser() throws Exception {
        Long memberId = newId();
        var event = new FavoriteRemovedEvent(UUID.randomUUID(), FAVORITED_AT.plusMinutes(30),
            new FavoriteRemovedEvent.Data(401L, memberId, PRODUCT_ID, FavoriteRemovalCause.USER));

        messages.send(COMMUNITY_TOPIC, event);

        AsyncWait.until(TIMEOUT, () -> !recordsOf(memberId).isEmpty());
        assertThat(recordsOf(memberId)).singleElement()
            .extracting(ActivityRecord::getType, ActivityRecord::getEventId, ActivityRecord::getFavoriteId)
            .containsExactly(ActivityType.FAVORITE_REMOVED, event.eventId(), 401L);
    }

    @Test
    @DisplayName("PREF-20 같은 찜을 두 번 받아도 찜 활동 기록은 한 줄이고 DLT로 보내지 않는다")
    void keepOneRecordForSameFavorite() throws Exception {
        Long memberId = newId();
        var event = favoriteAdded(memberId);

        messages.send(COMMUNITY_TOPIC, event);
        var second = messages.send(COMMUNITY_TOPIC, event);

        messages.awaitProcessed(COMMUNITY_TOPIC, second);
        assertThat(recordsOf(memberId)).hasSize(1);
        assertThat(messages.deadLetters(COMMUNITY_DLT, String.valueOf(memberId))).isEmpty();
    }

    @Test
    @DisplayName("커뮤니티 토픽에서 추천이 받지 않는 종류는 건너뛰고 DLT로 보내지 않으며, 뒤 메시지를 처리한다")
    void skipUnhandledCommunityEvent() throws Exception {
        Long memberId = newId();
        String key = String.valueOf(memberId);
        messages.sendRaw(COMMUNITY_TOPIC, key, "community.unknown", UUID.randomUUID(), "받지 않는 종류라 읽지 않는 본문");

        // 키가 같아 같은 파티션이므로, 뒤 메시지를 처리했으면 앞 메시지는 이미 처리됐다
        messages.send(COMMUNITY_TOPIC, favoriteAdded(memberId));

        AsyncWait.until(TIMEOUT, () -> !recordsOf(memberId).isEmpty());
        assertThat(messages.deadLetters(COMMUNITY_DLT, key)).isEmpty();
    }

    @Test
    @DisplayName("커뮤니티 메시지에 eventType 헤더가 없으면 재시도 없이 커뮤니티 DLT로 보낸다")
    void deadLetterCommunityEventWithoutEventType() throws Exception {
        String key = String.valueOf(newId());

        var sent = messages.sendRaw(COMMUNITY_TOPIC, key, null, UUID.randomUUID(), "어떤 이벤트인지 알 수 없는 본문");

        AsyncWait.until(TIMEOUT, () -> !messages.deadLetters(COMMUNITY_DLT, key).isEmpty());
        assertThat(messages.deadLetters(COMMUNITY_DLT, key)).singleElement()
            .satisfies(deadLetter -> assertThat(deadLetter.timestamp() - sent.timestamp())
                .isLessThan(RecommendKafkaMessages.RETRY_BACKOFF_TOTAL_MS));
    }

    @Test
    @DisplayName("PREF-21 찜 해제 원인이 추천이 모르는 값이면 읽지 못한 메시지로 보고 재시도 없이 커뮤니티 DLT로 보낸다")
    void deadLetterFavoriteRemovedWithUnknownCause() throws Exception {
        Long memberId = newId();
        String key = String.valueOf(memberId);
        UUID eventId = UUID.randomUUID();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", eventId);
        body.put("eventType", "favorite.removed");
        body.put("schemaVersion", 1);
        body.put("occurredAt", FAVORITED_AT.plusMinutes(30));
        body.put("producer", "community");
        body.put("aggregateId", "401");
        body.put("correlationId", key);
        body.put("data", Map.of("favoriteId", 401L, "memberId", memberId, "productId", PRODUCT_ID, "cause", "ADMIN"));

        var sent = messages.sendRaw(COMMUNITY_TOPIC, key, "favorite.removed", eventId,
            jsonMapper.writeValueAsString(body));

        AsyncWait.until(TIMEOUT, () -> !messages.deadLetters(COMMUNITY_DLT, key).isEmpty());
        assertThat(messages.deadLetters(COMMUNITY_DLT, key)).singleElement()
            .satisfies(deadLetter -> assertThat(deadLetter.timestamp() - sent.timestamp())
                .isLessThan(RecommendKafkaMessages.RETRY_BACKOFF_TOTAL_MS));
        assertThat(recordsOf(memberId)).isEmpty();
    }

    @Test
    @DisplayName("FIT-12 리뷰 변경됨을 커뮤니티 토픽에서 받아 리뷰 복사본으로 저장한다")
    void saveReviewChanged() throws Exception {
        Long reviewId = newId();
        var event = reviewChanged(reviewId, "SMALL", REVIEWED_AT);

        messages.send(COMMUNITY_TOPIC, event);

        AsyncWait.until(TIMEOUT, () -> replicaReviewRepository.findById(reviewId).isPresent());
        assertThat(replicaReviewRepository.findById(reviewId)).get()
            .extracting(RecommendReplicaReview::getOrderItemId, RecommendReplicaReview::getProductId,
                RecommendReplicaReview::getFit, RecommendReplicaReview::isVisible,
                RecommendReplicaReview::getEventOccurredAt)
            .containsExactly(601L, PRODUCT_ID, ReviewFit.SMALL, true, REVIEWED_AT);
    }

    @Test
    @DisplayName("FIT-12 같은 리뷰 변경됨을 두 번 받아도 리뷰 복사본은 그대로이고 DLT로 보내지 않는다")
    void keepReviewReplicaForSameReviewChanged() throws Exception {
        Long reviewId = newId();
        var event = reviewChanged(reviewId, "LARGE", REVIEWED_AT);

        messages.send(COMMUNITY_TOPIC, event);
        var second = messages.send(COMMUNITY_TOPIC, event);

        messages.awaitProcessed(COMMUNITY_TOPIC, second);
        assertThat(replicaReviewRepository.findById(reviewId)).get()
            .extracting(RecommendReplicaReview::getFit, RecommendReplicaReview::getEventOccurredAt)
            .containsExactly(ReviewFit.LARGE, REVIEWED_AT);
        assertThat(messages.deadLetters(COMMUNITY_DLT, String.valueOf(reviewId))).isEmpty();
    }

    @Test
    @DisplayName("FIT-12 리뷰 핏이 추천이 모르는 값이면 재시도 없이 커뮤니티 DLT로 보내고 리뷰 복사본을 남기지 않는다")
    void deadLetterReviewChangedWithUnknownFit() throws Exception {
        Long reviewId = newId();
        String key = String.valueOf(reviewId);

        var sent = messages.send(COMMUNITY_TOPIC, reviewChanged(reviewId, "TOO_BIG", REVIEWED_AT));

        AsyncWait.until(TIMEOUT, () -> !messages.deadLetters(COMMUNITY_DLT, key).isEmpty());
        assertThat(messages.deadLetters(COMMUNITY_DLT, key)).singleElement()
            .satisfies(deadLetter -> assertThat(deadLetter.timestamp() - sent.timestamp())
                .isLessThan(RecommendKafkaMessages.RETRY_BACKOFF_TOTAL_MS));
        assertThat(replicaReviewRepository.findById(reviewId)).isEmpty();
    }

    // 리뷰 ID(리뷰 이벤트의 키)만 바꾼 공개 리뷰다. 주문상품 601로 산 상품 31의 리뷰다
    private static ReviewChangedEvent reviewChanged(Long reviewId, String fit, LocalDateTime occurredAt) {
        return RecommendReplicaReviewFixture.reviewChangedEvent(reviewId, 601L, PRODUCT_ID, fit, true, occurredAt);
    }

    private List<ActivityRecord> recordsOf(Long memberId) {
        return activityRecordRepository.findAll().stream()
            .filter(record -> memberId.equals(record.getMemberId()))
            .toList();
    }

    private static Long newId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }

    // 회원 ID만 바꾼 찜이다. 회원이 상품 31을 찜 401로 찜했다
    private static FavoriteAddedEvent favoriteAdded(Long memberId) {
        return new FavoriteAddedEvent(UUID.randomUUID(), FAVORITED_AT,
            new FavoriteAddedEvent.Data(401L, memberId, PRODUCT_ID));
    }
}
