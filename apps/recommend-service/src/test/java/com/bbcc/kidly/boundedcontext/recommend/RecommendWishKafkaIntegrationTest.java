package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendKafkaMessages.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityType;
import com.bbcc.kidly.boundedcontext.recommend.out.activity.JpaActivityRecordRepository;
import com.bbcc.kidly.shared.order.event.WishCancelledEvent;
import com.bbcc.kidly.shared.order.event.WishSentEvent;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 추천이 주문 토픽의 조르기 · 조르기 철회를 받아 활동 기록에 저장하는지 확인한다(#1284).
 * 메시지는 팀 Kafka 규칙의 형식으로 토픽에 직접 넣는다. 주문은 다른 앱이라 이 테스트에서 띄우지 않는다.
 * 같은 토픽에는 다른 테스트의 메시지도 쌓여 이어서 받을 수 있으므로, 자녀 ID(조르기 이벤트의 키)를 테스트마다 새로 만든다.
 */
@KafkaIntegrationTest
class RecommendWishKafkaIntegrationTest {

    private static final String ORDER_TOPIC = "kidly.order.events";
    private static final String ORDER_DLT = "kidly.order.events.recommend.dlt";
    private static final LocalDateTime WISHED_AT = LocalDateTime.of(2026, 11, 3, 10, 3);
    private static final Long MEMBER_ID = 1L;

    @Autowired
    private JpaActivityRecordRepository activityRecordRepository;

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
    @DisplayName("PREF-18 조르기를 주문 토픽에서 받아 조른 상품마다 자녀의 조르기 활동 기록으로 저장한다")
    void saveWishSent() throws Exception {
        Long childId = newId();
        var event = wishSent(childId);

        messages.send(ORDER_TOPIC, event);

        AsyncWait.until(TIMEOUT, () -> recordsOf(childId).size() == 2);
        assertThat(recordsOf(childId))
            .extracting(ActivityRecord::getType, ActivityRecord::getWishRequestId, ActivityRecord::getProductId,
                ActivityRecord::getOccurredAt)
            .containsExactlyInAnyOrder(
                tuple(ActivityType.WISH_SENT, 301L, 31L, WISHED_AT),
                tuple(ActivityType.WISH_SENT, 301L, 41L, WISHED_AT));
    }

    @Test
    @DisplayName("PREF-19 직접 철회한 조르기를 주문 토픽에서 받아 그 조르기의 철회 기록으로 저장한다")
    void saveWishCancelled() throws Exception {
        Long childId = newId();
        var event = new WishCancelledEvent(UUID.randomUUID(), WISHED_AT.plusMinutes(5), new WishCancelledEvent.Data(
            301L, childId, MEMBER_ID, List.of(new WishCancelledEvent.Item(31L, 32L, 1)), "DIRECT"));

        messages.send(ORDER_TOPIC, event);

        AsyncWait.until(TIMEOUT, () -> !recordsOf(childId).isEmpty());
        assertThat(recordsOf(childId)).singleElement()
            .extracting(ActivityRecord::getType, ActivityRecord::getEventId, ActivityRecord::getWishRequestId)
            .containsExactly(ActivityType.WISH_CANCELLED, event.eventId(), 301L);
    }

    @Test
    @DisplayName("PREF-18 같은 조르기를 두 번 받아도 조른 상품마다 한 줄이고 DLT로 보내지 않는다")
    void keepOneRecordPerItemForSameWish() throws Exception {
        Long childId = newId();
        var event = wishSent(childId);

        messages.send(ORDER_TOPIC, event);
        var second = messages.send(ORDER_TOPIC, event);

        messages.awaitProcessed(ORDER_TOPIC, second);
        assertThat(recordsOf(childId)).hasSize(2);
        assertThat(messages.deadLetters(ORDER_DLT, String.valueOf(childId))).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"wish.paid", "wish.rejected", "wish.expired"})
    @DisplayName("PREF-18 조르기 결제 · 거절 · 만료는 기록하지 않고 DLT로 보내지 않으며, 뒤 메시지를 처리한다")
    void skipWishPaidRejectedExpired(String eventType) throws Exception {
        Long childId = newId();
        String key = String.valueOf(childId);
        messages.sendRaw(ORDER_TOPIC, key, eventType, UUID.randomUUID(), "받지 않는 종류라 읽지 않는 본문");

        // 키가 같아 같은 파티션이므로, 뒤 메시지를 처리했으면 앞 메시지는 이미 처리됐다
        messages.send(ORDER_TOPIC, wishSent(childId));

        AsyncWait.until(TIMEOUT, () -> recordsOf(childId).size() == 2);
        assertThat(recordsOf(childId)).extracting(ActivityRecord::getType).containsOnly(ActivityType.WISH_SENT);
        assertThat(messages.deadLetters(ORDER_DLT, key)).isEmpty();
    }

    private List<ActivityRecord> recordsOf(Long childId) {
        return activityRecordRepository.findAll().stream()
            .filter(record -> childId.equals(record.getChildProfileId()))
            .toList();
    }

    private static Long newId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }

    // 자녀 ID만 바꾼 조르기다. 회원 1의 자녀가 조르기 301로 상품 31 · 41을 졸랐다
    private static WishSentEvent wishSent(Long childId) {
        return new WishSentEvent(UUID.randomUUID(), WISHED_AT, new WishSentEvent.Data(301L, childId, MEMBER_ID,
            List.of(new WishSentEvent.Item(31L, 32L, 1), new WishSentEvent.Item(41L, 42L, 2))));
    }
}
