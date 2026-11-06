package com.bbcc.kidly.boundedcontext.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.auth.app.AuthDeadLetterService;
import com.bbcc.kidly.boundedcontext.auth.app.dto.AuthDeadLetterMessage;
import com.bbcc.kidly.boundedcontext.auth.domain.DeadLetterStatus;
import com.bbcc.kidly.boundedcontext.auth.domain.AuthDeadLetterRepository;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import com.bbcc.kidly.support.KafkaMessages;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@KafkaIntegrationTest
class AuthDeadLetterKafkaIntegrationTest {

    private static final String MEMBER_TOPIC = "kidly.member.events";
    private static final String CHILD_PROFILE_TOPIC = "kidly.childprofile.events";
    private static final List<String> LISTENER_IDS = List.of("authMemberEventListener",
        "authChildProfileEventListener", "authDeadLetterKafkaListener");
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthDeadLetterService deadLetterService;

    @Autowired
    private AuthDeadLetterRepository deadLetterRepository;

    private final String key = String.valueOf(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));
    private final List<MessageListenerContainer> listeners = new ArrayList<>();

    @BeforeEach
    void startListeners() {
        for (String id : LISTENER_IDS) {
            var listener = kafkaListenerEndpointRegistry.getListenerContainer(id);
            listener.start();
            listeners.add(listener);
        }
        AsyncWait.until(TIMEOUT, () -> listeners.stream().noneMatch(l -> l.getAssignedPartitions().isEmpty()));
    }

    @AfterEach
    void stopListeners() {
        listeners.forEach(MessageListenerContainer::stop);
    }

    @Test
    @DisplayName("읽지 못한 탈퇴 이벤트는 DLT를 거쳐 원래 위치 · 종류 · 예외 종류와 함께 1행으로 보관되고, 같은 위치는 다시 받아도 1행이다")
    void storesMemberDeadLetterOnce() throws Exception {
        var eventId = UUID.randomUUID();
        var body = "{\"data\":{\"memberId\":\"숫자 아님\"}}";
        var sent = new KafkaMessages(kafka.getBootstrapServers(), jsonMapper)
            .sendRaw(MEMBER_TOPIC, key, "member.withdrawn", eventId, body);

        AsyncWait.until(TIMEOUT, () -> deadLetterCount(eventId) == 1);
        var stored = deadLetterRepository.getById(deadLetterId(eventId));
        assertThat(stored.getStatus()).isEqualTo(DeadLetterStatus.OPEN);
        assertThat(stored.getDltTopic()).isEqualTo("kidly.member.events.auth.dlt");
        assertThat(stored.getSourceTopic()).isEqualTo(MEMBER_TOPIC);
        assertThat(stored.getSourcePartition()).isEqualTo(sent.partition());
        assertThat(stored.getSourceOffset()).isEqualTo(sent.offset());
        assertThat(stored.getMessageKey()).isEqualTo(key);
        assertThat(stored.getEventType()).isEqualTo("member.withdrawn");
        assertThat(stored.getEventId()).isEqualTo(eventId.toString());
        assertThat(stored.getReplayCount()).isZero();
        assertThat(JacksonException.class.isAssignableFrom(Class.forName(stored.getFailureType()))).isTrue();

        var again = new AuthDeadLetterMessage(stored.getDltTopic(), stored.getDltPartition(), stored.getDltOffset(),
            MEMBER_TOPIC, sent.partition(), sent.offset(), key, "member.withdrawn", eventId.toString(), body,
            stored.getFailureType());
        assertThat(deadLetterService.receive(again)).isFalse();
        assertThat(deadLetterCount(eventId)).isOne();
    }

    @Test
    @DisplayName("읽지 못한 자녀 프로필 이벤트는 자녀 프로필 DLT에서 원래 토픽과 함께 보관된다")
    void storesChildProfileDeadLetter() throws Exception {
        var eventId = UUID.randomUUID();
        var sent = new KafkaMessages(kafka.getBootstrapServers(), jsonMapper)
            .sendRaw(CHILD_PROFILE_TOPIC, key, "child.changed", eventId, "{\"data\":{\"childId\":\"숫자 아님\"}}");

        AsyncWait.until(TIMEOUT, () -> deadLetterCount(eventId) == 1);
        var stored = deadLetterRepository.getById(deadLetterId(eventId));
        assertThat(stored.getDltTopic()).isEqualTo("kidly.childprofile.events.auth.dlt");
        assertThat(stored.getSourceTopic()).isEqualTo(CHILD_PROFILE_TOPIC);
        assertThat(stored.getSourceOffset()).isEqualTo(sent.offset());
        assertThat(stored.getEventType()).isEqualTo("child.changed");
        assertThat(JacksonException.class.isAssignableFrom(Class.forName(stored.getFailureType()))).isTrue();
    }

    private long deadLetterCount(UUID eventId) {
        return jdbcTemplate.queryForObject("select count(*) from auth.dead_letter where event_id = ?", Long.class,
            eventId.toString());
    }

    private Long deadLetterId(UUID eventId) {
        return jdbcTemplate.queryForObject("select id from auth.dead_letter where event_id = ?", Long.class,
            eventId.toString());
    }
}
