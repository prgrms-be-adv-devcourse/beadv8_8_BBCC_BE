package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendKafkaMessages.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecord;
import com.bbcc.kidly.boundedcontext.recommend.out.activity.JpaActivityRecordRepository;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import java.time.LocalDateTime;
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
 * 추천이 회원 토픽의 회원 탈퇴를 받아 그 회원의 활동 기록을 지우는지 확인한다(#903).
 * 메시지는 팀 Kafka 규칙의 형식으로 토픽에 직접 넣는다. 회원은 다른 앱이라 이 테스트에서 띄우지 않는다.
 * 같은 토픽에는 다른 테스트의 메시지도 쌓여 이어서 받을 수 있으므로, 회원 ID(이벤트의 키)를 테스트마다 새로 만든다.
 */
@KafkaIntegrationTest
class RecommendMemberKafkaIntegrationTest {

    private static final String MEMBER_TOPIC = "kidly.member.events";
    private static final String MEMBER_DLT = "kidly.member.events.recommend.dlt";
    private static final LocalDateTime SEARCHED_AT = LocalDateTime.of(2026, 11, 3, 10, 0);
    private static final LocalDateTime WITHDRAWN_AT = LocalDateTime.of(2026, 11, 4, 9, 0);

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
    @DisplayName("회원 탈퇴를 회원 토픽에서 받아 그 회원의 활동 기록만 지운다")
    void deleteWithdrawnMemberActivities() throws Exception {
        Long memberId = newId();
        Long otherMemberId = newId();
        activityRecordRepository.saveAndFlush(search(memberId));
        activityRecordRepository.saveAndFlush(search(memberId));
        activityRecordRepository.saveAndFlush(search(otherMemberId));

        messages.send(MEMBER_TOPIC, withdrawn(memberId));

        AsyncWait.until(TIMEOUT, () -> recordsOf(memberId).isEmpty());
        assertThat(recordsOf(otherMemberId)).hasSize(1);
    }

    private List<ActivityRecord> recordsOf(Long memberId) {
        return activityRecordRepository.findAll().stream()
            .filter(record -> memberId.equals(record.getMemberId()))
            .toList();
    }

    private static Long newId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }

    private static ActivityRecord search(Long memberId) {
        return ActivityRecord.searched(new ActivityRecord.SearchInfo(UUID.randomUUID(), memberId, "맨투맨", 52L,
            Map.of("COLOR", List.of(3L, 5L)), 12L, SEARCHED_AT));
    }

    private static MemberWithdrawnEvent withdrawn(Long memberId) {
        return new MemberWithdrawnEvent(UUID.randomUUID(), WITHDRAWN_AT, new MemberWithdrawnEvent.Data(memberId));
    }
}
