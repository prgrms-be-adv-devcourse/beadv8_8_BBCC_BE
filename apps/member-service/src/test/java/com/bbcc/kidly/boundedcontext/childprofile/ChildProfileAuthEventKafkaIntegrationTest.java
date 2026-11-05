package com.bbcc.kidly.boundedcontext.childprofile;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.shared.auth.event.ChildAccountChangedEvent;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import com.bbcc.kidly.support.KafkaMessages;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

@KafkaIntegrationTest
class ChildProfileAuthEventKafkaIntegrationTest {
    @Autowired private KafkaListenerEndpointRegistry registry;
    @Autowired private KafkaContainer kafka;
    @Autowired private JsonMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    private MessageListenerContainer listener;
    private KafkaMessages messages;

    @BeforeEach
    void start() {
        messages = new KafkaMessages(kafka.getBootstrapServers(), mapper);
        listener = registry.getListenerContainer("childProfileAuthEventListener");
        listener.start();
        AsyncWait.until(Duration.ofSeconds(30), () -> !listener.getAssignedPartitions().isEmpty());
    }

    @AfterEach
    void stop() {
        listener.stop();
    }

    @Test
    void duplicateEventCreatesOneLink() throws Exception {
        var accountId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        var event = new ChildAccountChangedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new ChildAccountChangedEvent.Data(accountId, 42L, 11L, false));
        messages.send("kidly.auth.events", event);
        var sent = messages.send("kidly.auth.events", event);
        messages.awaitProcessed("kidly.childprofile", "kidly.auth.events", sent);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM childprofile.child_account_link WHERE account_id = ?",
            Long.class, accountId)).isEqualTo(1L);
    }

    @Test
    void skipsUnknownEventAndRoutesInvalidBodyToAuthDlt() throws Exception {
        var key = UUID.randomUUID().toString();
        var skipped = messages.sendRaw("kidly.auth.events", key, "auth.tokens-revoked", UUID.randomUUID(), "bad");
        messages.awaitProcessed("kidly.childprofile", "kidly.auth.events", skipped);
        var invalid = messages.sendRaw("kidly.auth.events", key, "child-account.changed", UUID.randomUUID(), "bad");
        messages.awaitProcessed("kidly.childprofile", "kidly.auth.events", invalid);
        AsyncWait.until(Duration.ofSeconds(30), () ->
            !messages.deadLetters("kidly.auth.events.childprofile.dlt", key).isEmpty());
        assertThat(messages.deadLetters("kidly.auth.events.childprofile.dlt", key)).singleElement()
            .satisfies(record -> assertThat(record.timestamp() - invalid.timestamp()).isLessThan(3000));
    }

    @Test
    void routesMessageWithoutEventTypeToAuthDltWithoutRetry() throws Exception {
        var key = UUID.randomUUID().toString();
        var sent = messages.sendRaw("kidly.auth.events", key, null, UUID.randomUUID(), "{}");
        AsyncWait.until(Duration.ofSeconds(30), () ->
            !messages.deadLetters("kidly.auth.events.childprofile.dlt", key).isEmpty());
        assertThat(messages.deadLetters("kidly.auth.events.childprofile.dlt", key)).singleElement()
            .satisfies(record -> assertThat(record.timestamp() - sent.timestamp()).isLessThan(3000));
    }
}
