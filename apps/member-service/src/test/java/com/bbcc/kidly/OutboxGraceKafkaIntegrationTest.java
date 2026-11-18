package com.bbcc.kidly;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.auth.AuthOutboxMessageFixture;
import com.bbcc.kidly.boundedcontext.auth.app.outbox.AuthOutboxRelay;
import com.bbcc.kidly.boundedcontext.auth.domain.outbox.AuthOutboxRepository;
import com.bbcc.kidly.boundedcontext.childprofile.ChildProfileOutboxMessageFixture;
import com.bbcc.kidly.boundedcontext.childprofile.app.outbox.ChildProfileOutboxRelayService;
import com.bbcc.kidly.boundedcontext.childprofile.domain.outbox.ChildProfileOutboxMessage;
import com.bbcc.kidly.boundedcontext.childprofile.domain.outbox.ChildProfileOutboxRepository;
import com.bbcc.kidly.boundedcontext.member.MemberOutboxMessageFixture;
import com.bbcc.kidly.boundedcontext.member.app.outbox.MemberOutboxRelay;
import com.bbcc.kidly.boundedcontext.member.domain.outbox.MemberOutboxMessage;
import com.bbcc.kidly.boundedcontext.member.domain.outbox.MemberOutboxRepository;
import com.bbcc.kidly.boundedcontext.seller.SellerOutboxMessageFixture;
import com.bbcc.kidly.boundedcontext.seller.app.outbox.SellerOutboxRelay;
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
    @Autowired private MemberOutboxRelay memberRelay;
    @Autowired private SellerOutboxRelay sellerRelay;
    @Autowired private ChildProfileOutboxRelayService childRelay;
    @Autowired private AuthOutboxRelay authRelay;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private KafkaContainer kafka;

    @Test
    void memberRelaysOnlyOld() {
        var old = UUID.randomUUID();
        var recent = UUID.randomUUID();
        members.save(new MemberOutboxMessage(MemberOutboxMessageFixture.withdrawn(old)));
        members.save(new MemberOutboxMessage(MemberOutboxMessageFixture.withdrawn(recent)));
        verifyRelay("member.member_outbox", "kidly.member.events", old, recent, memberRelay::relay);
    }

    @Test
    void sellerRelaysOnlyOld() {
        var old = UUID.randomUUID();
        var recent = UUID.randomUUID();
        sellers.save(new SellerOutboxMessage(SellerOutboxMessageFixture.changed(old)));
        sellers.save(new SellerOutboxMessage(SellerOutboxMessageFixture.changed(recent)));
        verifyRelay("seller.seller_outbox", "kidly.seller.events", old, recent, sellerRelay::relay);
    }

    @Test
    void childRelaysOnlyOld() {
        var old = UUID.randomUUID();
        var recent = UUID.randomUUID();
        children.save(ChildProfileOutboxMessage.create(ChildProfileOutboxMessageFixture.childChanged(old)));
        children.save(ChildProfileOutboxMessage.create(ChildProfileOutboxMessageFixture.childChanged(recent)));
        verifyRelay("childprofile.child_profile_outbox", "kidly.childprofile.events",
            old, recent, childRelay::relay);
    }

    @Test
    void authRelaysOnlyOld() {
        var old = UUID.randomUUID();
        var recent = UUID.randomUUID();
        auth.save(AuthOutboxMessageFixture.childAccountChanged(old));
        auth.save(AuthOutboxMessageFixture.childAccountChanged(recent));
        verifyRelay("auth.auth_outbox", "kidly.auth.events", old, recent, authRelay::relay);
    }

    private void verifyRelay(String table, String topic, UUID old, UUID recent, Runnable relay) {
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
        assertThat(jdbc.queryForObject(
            "select count(*) from " + table + " where event_id = ? and sent_at is not null", Integer.class, old))
            .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from " + table + " where event_id = ?", Integer.class, recent))
            .isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "select sent_at from " + table + " where event_id = ?", Object.class, recent))
            .isNull();
    }
}
