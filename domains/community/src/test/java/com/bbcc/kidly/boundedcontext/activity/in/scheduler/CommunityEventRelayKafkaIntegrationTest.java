package com.bbcc.kidly.boundedcontext.activity.in.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.activity.app.CommunityEventMetrics;
import com.bbcc.kidly.boundedcontext.favorite.app.FavoriteService;
import com.bbcc.kidly.boundedcontext.review.app.ReviewService;
import com.bbcc.kidly.boundedcontext.review.app.ReviewService.ImageUrlsInput;
import com.bbcc.kidly.boundedcontext.review.app.port.in.ReviewEligibilityRecorder;
import com.bbcc.kidly.boundedcontext.review.app.port.in.ReviewEligibilityRecorder.PurchaseConfirmed;
import com.bbcc.kidly.boundedcontext.support.AsyncWait;
import com.bbcc.kidly.boundedcontext.support.KafkaIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 릴레이가 커밋된 아웃박스 행을 kidly.community.events로 보낸다(Kafka 테스트는 보내기 스위치가 켜져 릴레이가 스스로 돈다):
 * 키 = correlationId(리뷰 ID · 회원 ID), 헤더 eventType · eventId, Java 타입 헤더 없음, 본문 = 공통 칸 7개 + data. 보낸 행은 지운다.
 */
@KafkaIntegrationTest
class CommunityEventRelayKafkaIntegrationTest {

    private static final String TOPIC = "kidly.community.events";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private ReviewEligibilityRecorder eligibilityRecorder;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private FavoriteService favoriteService;

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("리뷰 작성은 키 = 리뷰 ID, 찜은 키 = 회원 ID로 헤더 · 본문 공통 칸 7개와 함께 도착하고 아웃박스 행은 지워진다")
    void deliversWithKeyHeadersAndEnvelope() {
        long member = ThreadLocalRandom.current().nextLong(1_000_000, 9_000_000);
        long product = member;
        long orderItem = member;
        jdbcTemplate.update("insert into community.replica_product (product_id, name, thumbnail_url, display_status, "
            + "source_occurred_at, created_at, updated_at) values (?, '상품', null, 'ON_SALE', now(), now(), now())",
            product);
        eligibilityRecorder.record(new PurchaseConfirmed(orderItem, 1L, member, product, 1L, null,
            LocalDateTime.of(2026, 11, 12, 10, 0)));
        long reviewId = reviewService.write(member, "닉", String.valueOf(orderItem), 5, "좋아요",
            ReviewService.FitInput.absent(), ImageUrlsInput.absent()).reviewId();
        favoriteService.add(member, String.valueOf(product));

        List<ConsumerRecord<String, String>> review = awaitRecords(String.valueOf(reviewId), "review.changed");
        List<ConsumerRecord<String, String>> favorite = awaitRecords(String.valueOf(member), "favorite.added");

        assertThat(review).singleElement().satisfies(record -> {
            Map<String, Object> body = body(record);
            assertThat(body).containsOnlyKeys("eventId", "eventType", "schemaVersion", "occurredAt", "producer",
                "aggregateId", "correlationId", "data");
            assertThat(body).containsEntry("eventId", header(record, "eventId"))
                .containsEntry("eventType", "review.changed").containsEntry("producer", "community")
                .containsEntry("aggregateId", String.valueOf(reviewId))
                .containsEntry("correlationId", String.valueOf(reviewId));
            assertThat(UUID.fromString(header(record, "eventId"))).isNotNull();
            assertThat(record.headers().lastHeader("__TypeId__")).isNull();
            assertThat(record.headers().toArray()).hasSize(2);
            assertThat(asMap(body.get("data"))).containsEntry("visible", true).containsEntry("rating", 5);
        });
        assertThat(favorite).singleElement().satisfies(record -> {
            assertThat(asMap(body(record).get("data"))).containsEntry("memberId", (int) member)
                .containsEntry("productId", (int) product);
            assertThat(body(record)).containsEntry("correlationId", String.valueOf(member));
        });
        AsyncWait.until(TIMEOUT, () -> jdbcTemplate.queryForObject(
            "select count(*) from community.event_outbox", Long.class) == 0);
        assertThat(registry.get(CommunityEventMetrics.SCHEDULER_LAST_SUCCESS).tag("task", "event_relay").gauge()
            .value()).isPositive();
    }

    private List<ConsumerRecord<String, String>> awaitRecords(String key, String eventType) {
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        AsyncWait.until(TIMEOUT, () -> {
            found.clear();
            readAll().stream()
                .filter(record -> key.equals(record.key()) && eventType.equals(header(record, "eventType")))
                .forEach(found::add);
            return !found.isEmpty();
        });
        return found;
    }

    private List<ConsumerRecord<String, String>> readAll() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        try (var consumer = new KafkaConsumer<String, String>(properties)) {
            List<TopicPartition> partitions = consumer.partitionsFor(TOPIC).stream()
                .map(info -> new TopicPartition(TOPIC, info.partition())).toList();
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            Map<TopicPartition, Long> end = consumer.endOffsets(partitions);
            List<ConsumerRecord<String, String>> records = new ArrayList<>();
            long deadline = System.currentTimeMillis() + 5_000;
            while (partitions.stream().anyMatch(partition -> consumer.position(partition) < end.get(partition))
                && System.currentTimeMillis() < deadline) {
                consumer.poll(Duration.ofMillis(200)).forEach(records::add);
            }
            return records;
        }
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> body(ConsumerRecord<String, String> record) {
        return JSON.readValue(record.value(), Map.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }
}
