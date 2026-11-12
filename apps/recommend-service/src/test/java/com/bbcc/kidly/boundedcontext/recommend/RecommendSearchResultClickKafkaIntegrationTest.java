package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendKafkaMessages.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityType;
import com.bbcc.kidly.boundedcontext.recommend.out.activity.JpaActivityRecordRepository;
import com.bbcc.kidly.shared.search.event.SearchResultClickedEvent;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 추천이 검색 토픽의 검색 결과 클릭을 받아 활동 기록에 저장하는지 확인한다(#1284).
 * 메시지는 팀 Kafka 규칙의 형식으로 토픽에 직접 넣는다. 검색은 다른 앱이라 이 테스트에서 띄우지 않는다.
 * 같은 토픽에는 다른 테스트의 메시지도 쌓여 이어서 받을 수 있으므로, 회원 ID(키)를 테스트마다 새로 만든다.
 */
@KafkaIntegrationTest
class RecommendSearchResultClickKafkaIntegrationTest {

    private static final String SEARCH_TOPIC = "kidly.search.events";
    private static final String SEARCH_DLT = "kidly.search.events.recommend.dlt";
    private static final LocalDateTime CLICKED_AT = LocalDateTime.of(2026, 11, 3, 10, 1);
    private static final Long PRODUCT_ID = 31L;

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
    @DisplayName("PREF-17 검색 결과 클릭을 검색 토픽에서 받아 누른 상품의 활동 기록으로 저장한다")
    void saveSearchResultClick() throws Exception {
        Long memberId = newId();
        var event = searchResultClicked(memberId);

        messages.send(SEARCH_TOPIC, event);

        AsyncWait.until(TIMEOUT, () -> !recordsOf(memberId).isEmpty());
        assertThat(recordsOf(memberId)).singleElement()
            .extracting(
                ActivityRecord::getType,
                ActivityRecord::getEventId,
                ActivityRecord::getChildProfileId,
                ActivityRecord::getProductId,
                ActivityRecord::getOccurredAt)
            .containsExactly(ActivityType.SEARCH_RESULT_CLICKED, event.eventId(), null, PRODUCT_ID, CLICKED_AT);
    }

    @Test
    @DisplayName("PREF-17 같은 검색 결과 클릭을 두 번 받아도 활동 기록은 한 줄이고 DLT로 보내지 않는다")
    void keepOneRecordForSameClick() throws Exception {
        Long memberId = newId();
        var event = searchResultClicked(memberId);

        messages.send(SEARCH_TOPIC, event);
        var second = messages.send(SEARCH_TOPIC, event);

        messages.awaitProcessed(SEARCH_TOPIC, second);
        assertThat(recordsOf(memberId)).hasSize(1);
        assertThat(messages.deadLetters(SEARCH_DLT, String.valueOf(memberId))).isEmpty();
    }

    private List<ActivityRecord> recordsOf(Long memberId) {
        return activityRecordRepository.findAll().stream()
            .filter(record -> memberId.equals(record.getMemberId()))
            .toList();
    }

    private static Long newId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }

    // 회원 ID만 바꾼 클릭이다. 자녀를 고르지 않고 "맨투맨" 검색 결과의 세 번째 상품 31을 눌렀다
    private static SearchResultClickedEvent searchResultClicked(Long memberId) {
        return new SearchResultClickedEvent(UUID.randomUUID(), CLICKED_AT,
            new SearchResultClickedEvent.Data(memberId, "맨투맨", PRODUCT_ID, 3, "RELEVANCE", null));
    }
}
