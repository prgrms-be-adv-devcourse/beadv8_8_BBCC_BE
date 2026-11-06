package com.bbcc.kidly.boundedcontext.childprofile;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.childprofile.app.ChildProfileDeadLetterService;
import com.bbcc.kidly.boundedcontext.childprofile.app.dto.ChildProfileDeadLetterMessage;
import com.bbcc.kidly.boundedcontext.childprofile.domain.DeadLetterStatus;
import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfileDeadLetterRepository;
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
class ChildProfileDeadLetterKafkaIntegrationTest {

    private static final String MEMBER_TOPIC = "kidly.member.events";
    private static final String AUTH_TOPIC = "kidly.auth.events";
    private static final List<String> LISTENER_IDS = List.of("childProfileMemberEventListener",
        "childProfileAuthEventListener", "childProfileDeadLetterKafkaListener");
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
    private ChildProfileDeadLetterService deadLetterService;

    @Autowired
    private ChildProfileDeadLetterRepository deadLetterRepository;

    private final String key = String.valueOf(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));
    private final List<MessageListenerContainer> listeners = new ArrayList<>();

    @BeforeEach
    void startListeners() {
        for (var id : LISTENER_IDS) {
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
    @DisplayName("읽지 못한 회원 이벤트는 DLT를 거쳐 원래 위치 · 종류 · 예외 종류와 함께 1행으로 보관되고, 같은 위치는 다시 받아도 1행이다")
    void storesMemberDeadLetterOnce() throws Exception {
        var eventId = UUID.randomUUID();
        var body = "{\"data\":{\"memberId\":\"숫자 아님\"}}";
        var sent = new KafkaMessages(kafka.getBootstrapServers(), jsonMapper)
            .sendRaw(MEMBER_TOPIC, key, "member.withdrawn", eventId, body);

        AsyncWait.until(TIMEOUT, () -> deadLetterCount(eventId) == 1);
        var stored = deadLetterRepository.getById(deadLetterId(eventId));
        assertThat(stored.getStatus()).isEqualTo(DeadLetterStatus.OPEN);
        assertThat(stored.getDltTopic()).isEqualTo("kidly.member.events.childprofile.dlt");
        assertThat(stored.getSourceTopic()).isEqualTo(MEMBER_TOPIC);
        assertThat(stored.getSourcePartition()).isEqualTo(sent.partition());
        assertThat(stored.getSourceOffset()).isEqualTo(sent.offset());
        assertThat(stored.getMessageKey()).isEqualTo(key);
        assertThat(stored.getEventType()).isEqualTo("member.withdrawn");
        assertThat(stored.getEventId()).isEqualTo(eventId.toString());
        assertThat(stored.getReplayCount()).isZero();
        assertThat(JacksonException.class.isAssignableFrom(Class.forName(stored.getFailureType()))).isTrue();

        var again = new ChildProfileDeadLetterMessage(stored.getDltTopic(), stored.getDltPartition(),
            stored.getDltOffset(), MEMBER_TOPIC, sent.partition(), sent.offset(), key, "member.withdrawn",
            eventId.toString(), body, stored.getFailureType());
        assertThat(deadLetterService.receive(again)).isFalse();
        assertThat(deadLetterCount(eventId)).isOne();
    }

    @Test
    @DisplayName("읽지 못한 인증 이벤트도 인증 DLT를 거쳐 원래 토픽과 함께 보관된다")
    void storesAuthDeadLetter() throws Exception {
        var eventId = UUID.randomUUID();
        var sent = new KafkaMessages(kafka.getBootstrapServers(), jsonMapper)
            .sendRaw(AUTH_TOPIC, key, "child-account.changed", eventId, "{\"data\":{\"accountId\":\"숫자 아님\"}}");

        AsyncWait.until(TIMEOUT, () -> deadLetterCount(eventId) == 1);
        var stored = deadLetterRepository.getById(deadLetterId(eventId));
        assertThat(stored.getDltTopic()).isEqualTo("kidly.auth.events.childprofile.dlt");
        assertThat(stored.getSourceTopic()).isEqualTo(AUTH_TOPIC);
        assertThat(stored.getSourceOffset()).isEqualTo(sent.offset());
        assertThat(stored.getEventType()).isEqualTo("child-account.changed");
        assertThat(JacksonException.class.isAssignableFrom(Class.forName(stored.getFailureType()))).isTrue();
    }

    private long deadLetterCount(UUID eventId) {
        return jdbcTemplate.queryForObject("select count(*) from childprofile.dead_letter where event_id = ?",
            Long.class, eventId.toString());
    }

    private Long deadLetterId(UUID eventId) {
        return jdbcTemplate.queryForObject("select id from childprofile.dead_letter where event_id = ?", Long.class,
            eventId.toString());
    }
}
