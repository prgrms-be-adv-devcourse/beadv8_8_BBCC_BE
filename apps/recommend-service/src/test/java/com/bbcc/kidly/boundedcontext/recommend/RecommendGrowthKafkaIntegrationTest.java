package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendKafkaMessages.RETRY_BACKOFF_TOTAL_MS;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendKafkaMessages.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.growth.RecommendGrowthRecord;
import com.bbcc.kidly.boundedcontext.recommend.out.growth.JpaRecommendGrowthRecordRepository;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.event.ChildGrowthRecordedEvent;
import com.bbcc.kidly.shared.childprofile.event.ChildGrowthRecordDeletedEvent;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import java.math.BigDecimal;
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
 * 추천이 자녀 프로필 토픽의 성장 기록을 받아 저장하는지 확인한다.
 * 메시지는 팀 Kafka 규칙의 형식으로 토픽에 직접 넣는다. 회원 서비스는 다른 앱이라 이 테스트에서 띄우지 않는다.
 * 같은 토픽에는 다른 테스트의 메시지도 쌓여 이어서 받을 수 있으므로, 자녀 · 기록 ID를 테스트마다 새로 만든다.
 */
@KafkaIntegrationTest
class RecommendGrowthKafkaIntegrationTest {

    private static final String CHILD_TOPIC = "kidly.childprofile.events";
    private static final String CHILD_DLT = "kidly.childprofile.events.recommend.dlt";
    private static final LocalDateTime RECORDED_AT = LocalDateTime.of(2026, 11, 1, 9, 0);

    @Autowired
    private JpaRecommendGrowthRecordRepository growthRecordRepository;

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
    @DisplayName("성장 기록을 Kafka로 받아 성장 기록에 저장한다")
    void saveGrowthRecord() throws Exception {
        Long growthRecordId = newId();
        Long childId = newId();

        messages.send(CHILD_TOPIC, growthRecorded(growthRecordId, childId));

        AsyncWait.until(TIMEOUT, () -> growthRecordRepository.findById(growthRecordId).isPresent());
        RecommendGrowthRecord record = growthRecordRepository.findById(growthRecordId).orElseThrow();
        assertThat(record.getHeightCm()).isEqualByComparingTo("115.5");
        assertThat(record)
            .extracting(
                RecommendGrowthRecord::getChildProfileId,
                RecommendGrowthRecord::getBodyType,
                RecommendGrowthRecord::getRecordedAt)
            .containsExactly(childId, BodyType.SLIM, RECORDED_AT);
    }

    @Test
    @DisplayName("같은 성장 기록을 두 번 받아도 기록은 한 줄이고 DLT로 보내지 않는다")
    void keepOneRecordForSameGrowthRecord() throws Exception {
        Long growthRecordId = newId();
        Long childId = newId();

        messages.send(CHILD_TOPIC, growthRecorded(growthRecordId, childId));
        // 다시 보낸 이벤트는 eventId만 새 값이다
        var second = messages.send(CHILD_TOPIC, growthRecorded(growthRecordId, childId));

        messages.awaitProcessed(CHILD_TOPIC, second);
        assertThat(recordsOf(childId)).hasSize(1);
        assertThat(messages.deadLetters(CHILD_DLT, String.valueOf(childId))).isEmpty();
    }

    @Test
    @DisplayName("성장 기록에 키(cm) 값이 없으면 재시도 없이 DLT로 보내고 기록은 남기지 않는다")
    void deadLetterGrowthRecordWithoutHeight() throws Exception {
        Long growthRecordId = newId();
        Long childId = newId();
        var withoutHeight = new ChildGrowthRecordedEvent(UUID.randomUUID(), RECORDED_AT,
            new ChildGrowthRecordedEvent.Data(growthRecordId, childId, null, BodyType.SLIM, RECORDED_AT));

        var sent = messages.send(CHILD_TOPIC, withoutHeight);

        String key = String.valueOf(childId);
        AsyncWait.until(TIMEOUT, () -> !messages.deadLetters(CHILD_DLT, key).isEmpty());
        assertThat(messages.deadLetters(CHILD_DLT, key)).singleElement()
            .satisfies(deadLetter -> assertThat(deadLetter.timestamp() - sent.timestamp())
                .isLessThan(RETRY_BACKOFF_TOTAL_MS));
        assertThat(recordsOf(childId)).isEmpty();
    }

    private List<RecommendGrowthRecord> recordsOf(Long childId) {
        return growthRecordRepository.findAll().stream()
            .filter(record -> childId.equals(record.getChildProfileId()))
            .toList();
    }

    @Test
    @DisplayName("성장 기록 삭제를 두 번 받아도 기록은 삭제된 채로 남는다")
    void deleteGrowthRecordTwice() throws Exception {
        Long recordId = newId();
        Long childId = newId();
        messages.send(CHILD_TOPIC, growthRecorded(recordId, childId));
        AsyncWait.until(TIMEOUT, () -> growthRecordRepository.findById(recordId).isPresent());
        var deleted = new ChildGrowthRecordDeletedEvent(UUID.randomUUID(), RECORDED_AT,
            new ChildGrowthRecordDeletedEvent.Data(recordId, childId));
        messages.send(CHILD_TOPIC, deleted);
        var second = messages.send(CHILD_TOPIC, deleted);
        messages.awaitProcessed(CHILD_TOPIC, second);
        assertThat(growthRecordRepository.findById(recordId)).isEmpty();
        assertThat(messages.deadLetters(CHILD_DLT, String.valueOf(childId))).isEmpty();
    }

    private static Long newId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }

    // 기록 · 자녀 ID만 바꾼 성장 기록이다. 키는 115.5cm, 체형은 마름이다
    private static ChildGrowthRecordedEvent growthRecorded(Long growthRecordId, Long childId) {
        return new ChildGrowthRecordedEvent(UUID.randomUUID(), RECORDED_AT,
            new ChildGrowthRecordedEvent.Data(growthRecordId, childId, new BigDecimal("115.5"), BodyType.SLIM,
                RECORDED_AT));
    }
}
