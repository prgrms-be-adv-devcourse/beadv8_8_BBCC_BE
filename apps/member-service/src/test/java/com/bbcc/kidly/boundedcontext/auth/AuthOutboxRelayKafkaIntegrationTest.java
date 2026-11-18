package com.bbcc.kidly.boundedcontext.auth;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.auth.app.port.AuthEventOutbox;
import com.bbcc.kidly.shared.auth.event.ChildAccountChangedEvent;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import com.bbcc.kidly.support.AsyncWait;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

@KafkaIntegrationTest
class AuthOutboxRelayKafkaIntegrationTest {
    @Autowired private AuthEventOutbox outbox;
    @Autowired private TransactionTemplate transactions;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private KafkaContainer kafka;
    @Autowired private JsonMapper mapper;

    @Test
    void marksSentEventWithoutDeleting() {
        var event = new ChildAccountChangedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new ChildAccountChangedEvent.Data(100L, 200L, 300L, false));
        transactions.executeWithoutResult(status -> outbox.append(event));

        try (var consumer = subscribeAuthEvents()) {
            var deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            var found = false;
            while (!found && System.nanoTime() < deadline) {
                for (var record : consumer.poll(Duration.ofMillis(200))) {
                    if (record.value().contains(event.eventId().toString())) {
                        assertThat(record.key()).isEqualTo("100");
                        assertThat(record.headers().lastHeader("__TypeId__")).isNull();
                        assertThat(new String(record.headers().lastHeader("eventType").value(), UTF_8))
                            .isEqualTo("child-account.changed");
                        assertThat(new String(record.headers().lastHeader("eventId").value(), UTF_8))
                            .isEqualTo(event.eventId().toString());
                        var body = mapper.readTree(record.value());
                        assertThat(body.propertyNames()).containsExactly("eventId", "eventType", "schemaVersion",
                            "occurredAt", "producer", "aggregateId", "correlationId", "data");
                        assertThat(body.get("data").get("accountId").asLong()).isEqualTo(100L);
                        found = true;
                    }
                }
            }
            assertThat(found).isTrue();
        }
        AsyncWait.until(Duration.ofSeconds(30), () -> jdbc.queryForObject(
            "select count(*) from auth.auth_outbox where event_id = ? and sent_at is not null and attempt_count = 1",
            Long.class, event.eventId()) == 1);
    }

    @Test
    void sendsBothRows() {
        var first = new ChildAccountChangedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new ChildAccountChangedEvent.Data(101L, 200L, 300L, false));
        var second = new ChildAccountChangedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new ChildAccountChangedEvent.Data(101L, 200L, 300L, true));
        transactions.executeWithoutResult(status -> {
            outbox.append(first);
            outbox.append(second);
        });
        try (var consumer = subscribeAuthEvents()) {
            var ids = new ArrayList<String>();
            var deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (ids.size() < 2 && System.nanoTime() < deadline) {
                for (var record : consumer.poll(Duration.ofMillis(200))) {
                    var header = record.headers().lastHeader("eventId");
                    if (header != null) {
                        var id = new String(header.value(), UTF_8);
                        if (id.equals(first.eventId().toString()) || id.equals(second.eventId().toString())) {
                            ids.add(id);
                        }
                    }
                }
            }
            assertThat(ids).containsExactlyInAnyOrder(first.eventId().toString(), second.eventId().toString());
        }
    }

    private KafkaConsumer<String, String> subscribeAuthEvents() {
        var consumer = new KafkaConsumer<String, String>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
        consumer.subscribe(List.of("kidly.auth.events"));
        return consumer;
    }
}
