package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendKafkaMessages.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityType;
import com.bbcc.kidly.boundedcontext.recommend.out.activity.JpaActivityRecordRepository;
import com.bbcc.kidly.shared.order.event.CartItemAddedEvent;
import com.bbcc.kidly.shared.search.event.SearchPerformedEvent;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 추천이 검색 토픽의 검색 · 주문 토픽의 장바구니 담기를 받아 활동 기록에 저장하는지 확인한다(#752).
 * 메시지는 팀 Kafka 규칙의 형식으로 토픽에 직접 넣는다. 검색 · 주문은 다른 앱이라 이 테스트에서 띄우지 않는다.
 * 같은 토픽에는 다른 테스트의 메시지도 쌓여 이어서 받을 수 있으므로, 회원 ID(두 이벤트의 키)를 테스트마다 새로 만든다.
 */
@KafkaIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class RecommendActivityKafkaIntegrationTest {

    private static final String SEARCH_TOPIC = "kidly.search.events";
    private static final String ORDER_TOPIC = "kidly.order.events";
    private static final String SEARCH_DLT = "kidly.search.events.recommend.dlt";
    private static final String ORDER_DLT = "kidly.order.events.recommend.dlt";
    private static final LocalDateTime SEARCHED_AT = LocalDateTime.of(2026, 11, 3, 10, 0);
    private static final LocalDateTime ADDED_AT = LocalDateTime.of(2026, 11, 3, 10, 5);

    // 재시도는 1초 간격 3번이라, 재시도했다면 DLT에 들어가기까지 3초 이상 걸린다
    private static final long RETRY_BACKOFF_TOTAL_MS = 3_000;

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
    @DisplayName("검색을 Kafka로 받아 활동 기록에 저장한다")
    void saveSearch() throws Exception {
        Long memberId = newId();
        var event = searchPerformed(memberId);

        messages.send(SEARCH_TOPIC, event);

        AsyncWait.until(TIMEOUT, () -> !recordsOf(memberId).isEmpty());
        assertThat(recordsOf(memberId)).singleElement()
            .extracting(
                ActivityRecord::getType,
                ActivityRecord::getEventId,
                ActivityRecord::getKeyword,
                ActivityRecord::getCategoryId,
                ActivityRecord::getAttributeValueIds,
                ActivityRecord::getResultCount,
                ActivityRecord::getOccurredAt)
            .containsExactly(ActivityType.SEARCH, event.eventId(), "맨투맨", 52L, Map.of("COLOR", List.of(3L, 5L)),
                12L, SEARCHED_AT);
    }

    @Test
    @DisplayName("장바구니 담기를 주문 토픽에서 받아 활동 기록에 저장한다")
    void saveCartItemAdded() throws Exception {
        Long memberId = newId();
        var event = cartItemAdded(memberId);

        messages.send(ORDER_TOPIC, event);

        AsyncWait.until(TIMEOUT, () -> !recordsOf(memberId).isEmpty());
        assertThat(recordsOf(memberId)).singleElement()
            .extracting(
                ActivityRecord::getType,
                ActivityRecord::getEventId,
                ActivityRecord::getCartItemId,
                ActivityRecord::getChildProfileId,
                ActivityRecord::getProductId,
                ActivityRecord::getProductOptionId,
                ActivityRecord::getAddedQty,
                ActivityRecord::getCartQty,
                ActivityRecord::getOccurredAt)
            .containsExactly(ActivityType.CART_ITEM_ADDED, event.eventId(), 201L, 11L, 31L, 32L, 2, 3, ADDED_AT);
    }

    @Test
    @DisplayName("같은 검색을 두 번 받아도 활동 기록은 한 줄이고 DLT로 보내지 않는다")
    void keepOneRecordForSameSearch() throws Exception {
        Long memberId = newId();
        var event = searchPerformed(memberId);

        messages.send(SEARCH_TOPIC, event);
        var second = messages.send(SEARCH_TOPIC, event);

        messages.awaitProcessed(SEARCH_TOPIC, second);
        assertThat(recordsOf(memberId)).hasSize(1);
        assertThat(messages.deadLetters(SEARCH_DLT, String.valueOf(memberId))).isEmpty();
    }

    @Test
    @DisplayName("같은 장바구니 담기를 두 번 받아도 활동 기록은 한 줄이고 DLT로 보내지 않는다")
    void keepOneRecordForSameCartItemAdded() throws Exception {
        Long memberId = newId();
        var event = cartItemAdded(memberId);

        messages.send(ORDER_TOPIC, event);
        var second = messages.send(ORDER_TOPIC, event);

        messages.awaitProcessed(ORDER_TOPIC, second);
        assertThat(recordsOf(memberId)).hasSize(1);
        assertThat(messages.deadLetters(ORDER_DLT, String.valueOf(memberId))).isEmpty();
    }

    @Test
    @DisplayName("검색 토픽에서 추천이 받지 않는 종류는 건너뛰고 DLT로 보내지 않으며, 뒤 메시지를 처리한다")
    void skipUnknownSearchEvent() throws Exception {
        Long memberId = newId();
        String key = String.valueOf(memberId);
        messages.sendRaw(SEARCH_TOPIC, key, "search.unknown", UUID.randomUUID(), "받지 않는 종류라 읽지 않는 본문");

        // 키가 같아 같은 파티션이므로, 뒤 메시지를 처리했으면 앞 메시지는 이미 처리됐다
        messages.send(SEARCH_TOPIC, searchPerformed(memberId));

        AsyncWait.until(TIMEOUT, () -> !recordsOf(memberId).isEmpty());
        assertThat(messages.deadLetters(SEARCH_DLT, key)).isEmpty();
    }

    @Test
    @DisplayName("검색 메시지에 eventType 헤더가 없으면 재시도 없이 검색 DLT로 보낸다")
    void deadLetterSearchWithoutEventType() throws Exception {
        String key = String.valueOf(newId());

        var sent = messages.sendRaw(SEARCH_TOPIC, key, null, UUID.randomUUID(), "어떤 이벤트인지 알 수 없는 본문");

        AsyncWait.until(TIMEOUT, () -> !messages.deadLetters(SEARCH_DLT, key).isEmpty());
        assertThat(messages.deadLetters(SEARCH_DLT, key)).singleElement()
            .satisfies(deadLetter -> assertThat(deadLetter.timestamp() - sent.timestamp())
                .isLessThan(RETRY_BACKOFF_TOTAL_MS));
    }

    @Test
    @DisplayName("회원 ID가 없는 검색은 재시도 없이 검색 DLT로 보내고 활동 기록은 남기지 않으며, 로그에 검색어를 남기지 않는다")
    void deadLetterSearchWithoutMember(CapturedOutput output) throws Exception {
        // 회원 ID가 없으면 키도 없으므로, 이 테스트에서만 쓰는 검색어로 기록과 로그를 찾는다
        String keyword = "dlt-" + UUID.randomUUID();
        var withoutMember = new SearchPerformedEvent(UUID.randomUUID(), SEARCHED_AT,
            new SearchPerformedEvent.Data(null, keyword, null, Map.of(), 0L));
        String key = String.valueOf(newId());

        var sent = messages.sendRaw(SEARCH_TOPIC, key, withoutMember.eventType(), withoutMember.eventId(),
            body(withoutMember));

        AsyncWait.until(TIMEOUT, () -> !messages.deadLetters(SEARCH_DLT, key).isEmpty());
        assertThat(messages.deadLetters(SEARCH_DLT, key)).singleElement()
            .satisfies(deadLetter -> assertThat(deadLetter.timestamp() - sent.timestamp())
                .isLessThan(RETRY_BACKOFF_TOTAL_MS));
        assertThat(activityRecordRepository.findAll()).noneMatch(record -> keyword.equals(record.getKeyword()));
        assertThat(output.getOut()).contains("key=" + key).doesNotContain(keyword);
    }

    // 회원 ID가 없는 검색은 보내는 쪽이 만들지 않으므로 키(회원 ID)를 따로 정해 본문만 같은 형식으로 만든다
    private String body(SearchPerformedEvent event) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", event.eventId());
        body.put("eventType", event.eventType());
        body.put("schemaVersion", event.schemaVersion());
        body.put("occurredAt", event.occurredAt());
        body.put("producer", event.producer());
        body.put("aggregateId", event.aggregateId());
        body.put("correlationId", event.correlationId());
        body.put("data", event.data());
        return jsonMapper.writeValueAsString(body);
    }

    private List<ActivityRecord> recordsOf(Long memberId) {
        return activityRecordRepository.findAll().stream()
            .filter(record -> memberId.equals(record.getMemberId()))
            .toList();
    }

    private static Long newId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }

    // 회원 ID만 바꾼 검색이다. "맨투맨"을 카테고리 52, 색상 3 · 5로 찾아 12개가 나왔다
    private static SearchPerformedEvent searchPerformed(Long memberId) {
        return new SearchPerformedEvent(UUID.randomUUID(), SEARCHED_AT, new SearchPerformedEvent.Data(memberId,
            "맨투맨", 52L, Map.of("COLOR", List.of(3L, 5L)), 12L));
    }

    // 회원 ID만 바꾼 장바구니 담기다. 자녀 11을 고르고 상품 31의 옵션 32를 2개 담아 장바구니 줄 201이 3개가 됐다
    private static CartItemAddedEvent cartItemAdded(Long memberId) {
        return new CartItemAddedEvent(UUID.randomUUID(), ADDED_AT,
            new CartItemAddedEvent.Data(201L, memberId, 11L, 31L, 32L, 2, 3));
    }
}
