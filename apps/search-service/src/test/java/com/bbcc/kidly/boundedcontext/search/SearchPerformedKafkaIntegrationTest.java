package com.bbcc.kidly.boundedcontext.search;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.search.app.ProductListService;
import com.bbcc.kidly.boundedcontext.search.app.SearchIndexService;
import com.bbcc.kidly.boundedcontext.search.app.dto.ProductListRequest;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import com.bbcc.kidly.support.SearchIndexes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 로그인 회원이 검색하면 search.performed가 kidly.search.events에 나가는지 실제 Kafka로 본다(#611).
 * 토픽은 검색이 NewTopic으로 선언한다(@KafkaIntegrationTest가 기동 때 토픽 생성을 켠다).
 */
@KafkaIntegrationTest
class SearchPerformedKafkaIntegrationTest {

    private static final String TOPIC = "kidly.search.events";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private ProductListService productListService;

    @Autowired
    private SearchIndexService indexService;

    @Autowired
    private SearchIndexes indexes;

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private JsonMapper jsonMapper;

    // 다른 테스트가 넣은 메시지와 섞이지 않게 회원(키)을 매번 다르게 한다
    private final long memberId = ThreadLocalRandom.current().nextLong(1, 1_000_000_000L);

    @Test
    @DisplayName("키는 회원 ID, 헤더는 eventType · eventId, 본문은 공통 칸 7개 + data다")
    void sendsSearchPerformed() {
        indexService.applyProductChanged(
            SearchEvents.product(1L, "여아 원피스", 7L, 3L, ProductStatus.ON_SALE, 15_000, 3L, 1));
        indexes.refresh();

        productListService.list(
            new ProductListRequest(3L, " 원피스 ", List.of("COLOR:3"), null, null, null), memberId);

        AsyncWait.until(TIMEOUT, () -> !records().isEmpty());
        ConsumerRecord<String, String> record = records().getFirst();
        String eventId = header(record, "eventId");
        assertThat(header(record, "eventType")).isEqualTo("search.performed");
        assertThat(eventId).isNotBlank();
        assertThat(record.headers().lastHeader("__TypeId__")).isNull();

        JsonNode body = jsonMapper.readTree(record.value());
        assertThat(body.get("eventId").asString()).isEqualTo(eventId);
        assertThat(body.get("eventType").asString()).isEqualTo("search.performed");
        assertThat(body.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(body.get("occurredAt").asString()).isNotBlank();
        assertThat(body.get("producer").asString()).isEqualTo("search");
        assertThat(body.get("aggregateId").asString()).isEqualTo(String.valueOf(memberId));
        assertThat(body.get("correlationId").asString()).isEqualTo(String.valueOf(memberId));
        JsonNode data = body.get("data");
        assertThat(data.get("memberId").asLong()).isEqualTo(memberId);
        assertThat(data.get("keyword").asString()).isEqualTo("원피스");
        assertThat(data.get("categoryId").asLong()).isEqualTo(3L);
        assertThat(data.get("attributeValueIds").get("COLOR").get(0).asLong()).isEqualTo(3L);
        assertThat(data.get("resultCount").asLong()).isEqualTo(1L);
    }

    @Test
    @DisplayName("비로그인 검색은 보내지 않는다")
    void anonymousDoesNotSend() {
        productListService.list(new ProductListRequest(null, "원피스", null, null, null, null), null);
        // 비교용으로 같은 회원 키의 로그인 검색을 뒤에 보낸다. 이것이 도착했을 때 앞선 메시지가 없으면 보내지 않은 것이다
        productListService.list(new ProductListRequest(null, "바지", null, null, null, null), memberId);

        AsyncWait.until(TIMEOUT, () -> !records().isEmpty());
        assertThat(records()).singleElement()
            .satisfies(record -> assertThat(record.value()).contains("바지").doesNotContain("원피스"));
    }

    // 토픽을 처음부터 지금 끝까지 읽어 이 테스트 회원의 메시지만 돌려준다
    private List<ConsumerRecord<String, String>> records() {
        List<TopicPartition> partitions = IntStream.range(0, 3).mapToObj(p -> new TopicPartition(TOPIC, p)).toList();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            Map<TopicPartition, Long> ends = consumer.endOffsets(partitions);
            List<ConsumerRecord<String, String>> records = new ArrayList<>();
            while (partitions.stream().anyMatch(p -> consumer.position(p) < ends.get(p))) {
                consumer.poll(Duration.ofMillis(200)).forEach(records::add);
            }
            return records.stream().filter(record -> String.valueOf(memberId).equals(record.key())).toList();
        }
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), UTF_8);
    }
}
