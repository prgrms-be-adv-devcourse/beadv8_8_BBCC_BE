package com.bbcc.kidly.boundedcontext.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.auth.out.JpaChildProfileReplicaRepository;
import com.bbcc.kidly.boundedcontext.auth.domain.ChildAccount;
import com.bbcc.kidly.boundedcontext.auth.domain.ChildAccountRepository;
import com.bbcc.kidly.boundedcontext.auth.app.RefreshTokenService;
import com.bbcc.kidly.boundedcontext.auth.domain.RefreshTokenRepository;
import com.bbcc.kidly.boundedcontext.auth.domain.RefreshToken;
import com.bbcc.kidly.shared.auth.PrincipalId;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import com.bbcc.kidly.support.KafkaMessages;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

@KafkaIntegrationTest
class AuthChildProfileEventKafkaIntegrationTest {

    private static final String TOPIC = "kidly.childprofile.events";
    private static final String DLT = TOPIC + ".auth.dlt";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private JpaChildProfileReplicaRepository repository;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private KafkaListenerEndpointRegistry registry;
    @Autowired
    private KafkaContainer kafka;
    @Autowired
    private ChildAccountRepository accounts;
    @Autowired
    private RefreshTokenService refreshTokens;
    @Autowired
    private RefreshTokenRepository tokens;

    private final Long childId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    private KafkaMessages messages;
    private MessageListenerContainer listener;

    @BeforeEach
    void startListener() throws Exception {
        var config = Map.<String, Object>of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        try (var admin = Admin.create(config)) {
            if (!admin.listTopics().names().get().contains(TOPIC)) {
                admin.createTopics(List.of(new NewTopic(TOPIC, 3, (short) 1))).all().get();
            }
        }
        messages = new KafkaMessages(kafka.getBootstrapServers(), jsonMapper);
        listener = registry.getListenerContainer("authChildProfileEventListener");
        listener.start();
        AsyncWait.until(TIMEOUT, () -> !listener.getAssignedPartitions().isEmpty());
    }

    @AfterEach
    void stopListener() {
        listener.stop();
    }

    @Test
    void createsOnceAndDeletesReplica() throws Exception {
        var body = "{\"data\":{\"childId\":" + childId + ",\"memberId\":2,\"deleted\":false}}";
        messages.sendRaw(TOPIC, childId.toString(), "child.changed", UUID.randomUUID(), body);
        var duplicate = messages.sendRaw(TOPIC, childId.toString(), "child.changed", UUID.randomUUID(), body);
        messages.awaitProcessed("kidly.auth", TOPIC, duplicate);
        assertThat(repository.findById(childId)).get()
            .satisfies(replica -> assertThat(replica.getGuardianId()).isEqualTo(2L));

        var deleted = body.replace("false", "true");
        var sent = messages.sendRaw(TOPIC, childId.toString(), "child.changed", UUID.randomUUID(), deleted);
        messages.awaitProcessed("kidly.auth", TOPIC, sent);
        assertThat(repository.findById(childId)).isEmpty();
    }

    @Test
    void unreadableBodyGoesToChildDltWithoutRetry() throws Exception {
        var key = childId.toString();
        var sent = messages.sendRaw(TOPIC, key, "child.changed", UUID.randomUUID(), "{");
        AsyncWait.until(TIMEOUT, () -> !messages.deadLetters(DLT, key).isEmpty());
        assertThat(messages.deadLetters(DLT, key)).singleElement()
            .satisfies(record -> assertThat(record.timestamp() - sent.timestamp()).isLessThan(3000));
    }

    @Test
    void deletionRemovesAccountTokensAndPublishesRevocation() throws Exception {
        var body = "{\"data\":{\"childId\":" + childId + ",\"memberId\":2,\"deleted\":false}}";
        var created = messages.sendRaw(TOPIC, childId.toString(), "child.changed", UUID.randomUUID(), body);
        messages.awaitProcessed("kidly.auth", TOPIC, created);
        var account = accounts.saveAccount(ChildAccount.create(UUID.randomUUID().toString().substring(0, 16),
            "hash", 2L, childId));
        var token = refreshTokens.issue(PrincipalId.child(account.getId()));
        try (var consumer = new KafkaConsumer<String, String>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of("kidly.auth.events"));
            var deleted = messages.sendRaw(TOPIC, childId.toString(), "child.changed", UUID.randomUUID(),
                body.replace("false", "true"));
            messages.awaitProcessed("kidly.auth", TOPIC, deleted);
            assertThat(repository.findById(childId)).isEmpty();
            assertThat(accounts.findByChildProfileId(childId)).isEmpty();
            assertThat(tokens.findByTokenHash(RefreshToken.hashOf(token))).isEmpty();
            var found = new java.util.ArrayList<String>();
            AsyncWait.until(TIMEOUT, () -> {
                consumer.poll(Duration.ofMillis(200)).forEach(record -> {
                    if (account.getId().toString().equals(record.key())
                        && record.value().contains("\"principalType\":\"CHILD\"")) {
                        found.add(record.value());
                    }
                });
                return !found.isEmpty();
            });
            var data = jsonMapper.readTree(found.getFirst()).get("data");
            assertThat(data.get("principalType").asString()).isEqualTo("CHILD");
            assertThat(data.get("principalId").asLong()).isEqualTo(account.getId());
        }
    }
}
