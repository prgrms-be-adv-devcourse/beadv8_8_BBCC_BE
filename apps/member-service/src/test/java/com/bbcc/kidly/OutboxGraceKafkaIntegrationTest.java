package com.bbcc.kidly;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.auth.app.outbox.AuthOutboxRelayService;
import com.bbcc.kidly.boundedcontext.auth.domain.outbox.AuthOutboxMessage;
import com.bbcc.kidly.boundedcontext.auth.domain.outbox.AuthOutboxRepository;
import com.bbcc.kidly.boundedcontext.childprofile.ChildProfileOutboxMessageFixture;
import com.bbcc.kidly.boundedcontext.childprofile.app.outbox.ChildProfileOutboxRelayService;
import com.bbcc.kidly.boundedcontext.childprofile.domain.outbox.ChildProfileOutboxMessage;
import com.bbcc.kidly.boundedcontext.childprofile.domain.outbox.ChildProfileOutboxRepository;
import com.bbcc.kidly.boundedcontext.member.MemberOutboxMessageFixture;
import com.bbcc.kidly.boundedcontext.member.app.outbox.MemberOutboxRelayService;
import com.bbcc.kidly.boundedcontext.member.domain.outbox.MemberOutboxMessage;
import com.bbcc.kidly.boundedcontext.member.domain.outbox.MemberOutboxRepository;
import com.bbcc.kidly.boundedcontext.seller.SellerOutboxMessageFixture;
import com.bbcc.kidly.boundedcontext.seller.app.outbox.SellerOutboxRelayService;
import com.bbcc.kidly.boundedcontext.seller.domain.outbox.SellerOutboxMessage;
import com.bbcc.kidly.boundedcontext.seller.domain.outbox.SellerOutboxRepository;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.kafka.KafkaContainer;

@KafkaIntegrationTest
class OutboxGraceKafkaIntegrationTest {
    @Autowired private MemberOutboxRepository members;
    @Autowired private SellerOutboxRepository sellers;
    @Autowired private ChildProfileOutboxRepository children;
    @Autowired private AuthOutboxRepository auth;
    @Autowired private MemberOutboxRelayService memberRelay;
    @Autowired private SellerOutboxRelayService sellerRelay;
    @Autowired private ChildProfileOutboxRelayService childRelay;
    @Autowired private AuthOutboxRelayService authRelay;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private KafkaContainer kafka;

    @Test
    void memberRelaysOnlyOld() {
        var old = UUID.randomUUID();
        var recent = UUID.randomUUID();
        members.save(MemberOutboxMessage.create(MemberOutboxMessageFixture.withdrawn(old)));
        members.save(MemberOutboxMessage.create(MemberOutboxMessageFixture.withdrawn(recent)));
        verifyRelay("member.member_outbox", "kidly.member.events", old, recent, memberRelay::relay, false);
    }

    @Test
    void sellerRelaysOnlyOld() {
        var old = UUID.randomUUID();
        var recent = UUID.randomUUID();
        sellers.save(SellerOutboxMessage.create(SellerOutboxMessageFixture.changed(old)));
        sellers.save(SellerOutboxMessage.create(SellerOutboxMessageFixture.changed(recent)));
        verifyRelay("seller.seller_outbox", "kidly.seller.events", old, recent, sellerRelay::relay, false);
    }

    @Test
    void childRelaysOnlyOld() {
        var old = UUID.randomUUID();
        var recent = UUID.randomUUID();
        children.save(ChildProfileOutboxMessage.create(ChildProfileOutboxMessageFixture.childChanged(old)));
        children.save(ChildProfileOutboxMessage.create(ChildProfileOutboxMessageFixture.childChanged(recent)));
        verifyRelay("childprofile.child_profile_outbox", "kidly.childprofile.events",
            old, recent, childRelay::relay, false);
    }

    @Test
    void authRelaysOnlyOld() {
        var old = UUID.randomUUID();
        var recent = UUID.randomUUID();
        auth.save(AuthOutboxMessage.create(old, "child-account.changed", "1", "{}"));
        auth.save(AuthOutboxMessage.create(recent, "child-account.changed", "2", "{}"));
        verifyRelay("auth.auth_outbox", "kidly.auth.events", old, recent, authRelay::relay, true);
    }

    private void verifyRelay(String table, String topic, UUID old, UUID recent, Runnable relay, boolean deleted) {
        jdbc.update("update " + table + " set created_at = ? where event_id = ?",
            LocalDateTime.now().minusMinutes(6), old);
        try (var consumer = new KafkaConsumer<String, String>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of(topic));
            relay.run();
            var found = new boolean[1];
            AsyncWait.until(Duration.ofSeconds(30), () -> {
                consumer.poll(Duration.ofMillis(200)).forEach(record -> {
                    var header = record.headers().lastHeader("eventId");
                    if (header != null && old.toString().equals(new String(header.value(), StandardCharsets.UTF_8))) {
                        found[0] = true;
                    }
                });
                return found[0];
            });
        }
        var oldCondition = deleted ? "event_id = ?" : "event_id = ? and sent_at is null";
        assertThat(jdbc.queryForObject("select count(*) from " + table + " where " + oldCondition, Integer.class, old))
            .isZero();
        assertThat(jdbc.queryForObject("select count(*) from " + table + " where event_id = ?", Integer.class, recent))
            .isEqualTo(1);
        if (!deleted) {
            assertThat(jdbc.queryForObject(
                "select sent_at from " + table + " where event_id = ?", Object.class, recent))
                .isNull();
        }
    }
}
