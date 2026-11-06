package com.bbcc.kidly.boundedcontext.seller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.seller.app.SellerDeadLetterService;
import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerDeadLetterMessage;
import com.bbcc.kidly.boundedcontext.seller.domain.DeadLetterStatus;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerDeadLetterRepository;
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
class SellerDeadLetterKafkaIntegrationTest {

    private static final String TOPIC = "kidly.member.events";
    private static final String DLT = "kidly.member.events.seller.dlt";
    private static final List<String> LISTENER_IDS = List.of("sellerMemberEventListener",
        "sellerDeadLetterKafkaListener");
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
    private SellerDeadLetterService deadLetterService;

    @Autowired
    private SellerDeadLetterRepository deadLetterRepository;

    private final String memberId = String.valueOf(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));
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
    @DisplayName("읽지 못한 회원 이벤트는 DLT를 거쳐 원래 위치 · 종류 · 예외 종류와 함께 1행으로 보관되고, 같은 위치는 다시 받아도 1행이다")
    void storesDeadLetterOnce() throws Exception {
        var eventId = UUID.randomUUID();
        var body = "{\"data\":{\"memberId\":\"숫자 아님\",\"nickname\":\"하은맘\"}}";
        var sent = new KafkaMessages(kafka.getBootstrapServers(), jsonMapper)
            .sendRaw(TOPIC, memberId, "member.changed", eventId, body);

        AsyncWait.until(TIMEOUT, () -> deadLetterCount(eventId) == 1);
        var stored = deadLetterRepository.getById(deadLetterId(eventId));
        assertThat(stored.getStatus()).isEqualTo(DeadLetterStatus.OPEN);
        assertThat(stored.getDltTopic()).isEqualTo(DLT);
        assertThat(stored.getSourceTopic()).isEqualTo(TOPIC);
        assertThat(stored.getSourcePartition()).isEqualTo(sent.partition());
        assertThat(stored.getSourceOffset()).isEqualTo(sent.offset());
        assertThat(stored.getMessageKey()).isEqualTo(memberId);
        assertThat(stored.getEventType()).isEqualTo("member.changed");
        assertThat(stored.getEventId()).isEqualTo(eventId.toString());
        assertThat(stored.getReplayCount()).isZero();
        assertThat(JacksonException.class.isAssignableFrom(Class.forName(stored.getFailureType()))).isTrue();

        var again = new SellerDeadLetterMessage(stored.getDltTopic(), stored.getDltPartition(), stored.getDltOffset(),
            TOPIC, sent.partition(), sent.offset(), memberId, "member.changed", eventId.toString(), body,
            stored.getFailureType());
        assertThat(deadLetterService.receive(again)).isFalse();
        assertThat(deadLetterCount(eventId)).isOne();
    }

    private long deadLetterCount(UUID eventId) {
        return jdbcTemplate.queryForObject("select count(*) from seller.dead_letter where event_id = ?", Long.class,
            eventId.toString());
    }

    private Long deadLetterId(UUID eventId) {
        return jdbcTemplate.queryForObject("select id from seller.dead_letter where event_id = ?", Long.class,
            eventId.toString());
    }
}
