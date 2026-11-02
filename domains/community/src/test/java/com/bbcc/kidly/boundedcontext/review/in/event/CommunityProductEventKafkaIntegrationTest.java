package com.bbcc.kidly.boundedcontext.review.in.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.support.AsyncWait;
import com.bbcc.kidly.boundedcontext.support.KafkaIntegrationTest;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 커뮤니티가 kidly.product.events의 product.changed를 그룹 kidly.community로 받는다. 테스트가 보내는 쪽 규칙대로 메시지를 직접 넣는다.
 * 리스너를 켠 뒤 쌓여 있던 메시지(다른 테스트의 상품 이벤트)를 다 처리한 다음 복사본을 비우고 시작한다.
 * 처리 실패는 한 상품 행만 막는 CHECK 제약(NOT VALID)으로 만든다.
 */
@KafkaIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class CommunityProductEventKafkaIntegrationTest {

    private static final String TOPIC = "kidly.product.events";
    private static final String BREAK_REPLICA = "test_break_community_replica_product";
    private static final long RETRY_BACKOFF_TOTAL_MS = 3_000;
    // 나노초가 있는 고정 시각(DB에는 마이크로초로 내림 저장되는 값)
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 11, 2, 10, 0, 0, 123_456_123);

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private CommunityKafkaListenerTestGuard guard;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private CommunityKafkaTestClient kafkaClient;
    private MessageListenerContainer listener;

    @BeforeEach
    void startListener() {
        kafkaClient = new CommunityKafkaTestClient(kafka, jsonMapper);
        listener = kafkaClient.start(guard, CommunityProductEventKafkaListener.ID, TOPIC);
        jdbcTemplate.update("delete from community.replica_product");
    }

    @AfterEach
    void stopListener() {
        listener.stop();
        jdbcTemplate.execute("alter table community.replica_product drop constraint if exists " + BREAK_REPLICA);
    }

    @Test
    @DisplayName("product.changed를 받아 복사본과 표시 상태를 저장한다")
    void productChangedStored() {
        long productId = newProductId();

        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, changed(productId, ProductStatus.ON_SALE, OCCURRED_AT)));

        assertThat(stored(productId)).containsEntry("name", "상품 A").containsEntry("display_status", "ON_SALE");
    }

    @Test
    @DisplayName("같은 product.changed를 두 번 받아도 행 1개 · 값 1회분(수정 시각 그대로)이고 DLT로 보내지 않는다")
    void duplicateAppliedOnce() {
        long productId = newProductId();
        var event = changed(productId, ProductStatus.ON_SALE, OCCURRED_AT);

        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, event));
        Map<String, Object> first = stored(productId);
        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, event));

        assertThat(stored(productId)).isEqualTo(first);
        assertThat(rowCount(productId)).isEqualTo(1);
        assertThat(kafkaClient.deadLetters(TOPIC, event.eventId().toString())).isEmpty();
    }

    @Test
    @DisplayName("모르는 상품 판매 상태 · 모르는 필드가 있어도 읽고, 판매 종료로 저장한다")
    void unknownStatusIsSaleEnded() {
        long productId = newProductId();
        var event = changed(productId, ProductStatus.ON_SALE, OCCURRED_AT);
        Map<String, Object> body = kafkaClient.envelopeFields(event);
        body.put("traceId", "새 공통 칸");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = jsonMapper.convertValue(event.data(), Map.class);
        data.put("status", "NEW_STATUS");
        data.put("newField", "새 값");
        body.put("data", data);

        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, event.correlationId(), event.eventType(),
            event.eventId().toString(), kafkaClient.toJson(body)));

        assertThat(stored(productId)).containsEntry("display_status", "SALE_ENDED");
        assertThat(kafkaClient.deadLetters(TOPIC, event.eventId().toString())).isEmpty();
    }

    @Test
    @DisplayName("모르는 옵션 판매 상태는 보지 않는다: 남은 판매 중 옵션이 모두 품절이면 품절, 판매 중 옵션이 없어지면 판매 종료")
    void unknownOptionStatusNotCountedAsOnSale() {
        long soldOutId = newProductId();
        long saleEndedId = soldOutId + 1;
        // 재고 있는 옵션을 모르는 상태로 바꾼다: 남은 판매 중 옵션(품절)만 보면 품절
        String soldOut = sendWithUnknownOptionStatus(soldOutId, List.of(true, false), 1);
        // 하나뿐인 판매 중 옵션을 모르는 상태로 바꾼다: 판매 중 옵션이 없어 판매 종료
        String saleEnded = sendWithUnknownOptionStatus(saleEndedId, List.of(false), 0);

        assertThat(stored(soldOutId)).containsEntry("display_status", "SOLD_OUT");
        assertThat(stored(saleEndedId)).containsEntry("display_status", "SALE_ENDED");
        assertThat(kafkaClient.deadLetters(TOPIC, soldOut)).isEmpty();
        assertThat(kafkaClient.deadLetters(TOPIC, saleEnded)).isEmpty();
    }

    /**
     * 판매 중 옵션들(품절 여부 순서대로)로 이벤트를 만들고, unknownIndex 옵션의 saleStatus만 모르는 값으로 바꿔 보낸 뒤 처리를 기다린다.
     *
     * @return 보낸 eventId
     */
    private String sendWithUnknownOptionStatus(long productId, List<Boolean> soldOuts, int unknownIndex) {
        List<ProductChangedEvent.Option> options = new ArrayList<>();
        for (int i = 0; i < soldOuts.size(); i++) {
            options.add(new ProductChangedEvent.Option((long) i + 1, "옵션 " + i, "110", 25_000L, null, null,
                soldOuts.get(i), OptionSaleStatus.ON_SALE));
        }
        var event = new ProductChangedEvent(UUID.randomUUID(), OCCURRED_AT, new ProductChangedEvent.Data(productId, 2L,
            "상품 A", 3L, ProductStatus.ON_SALE, null, OCCURRED_AT.minusDays(1), false, options));
        Map<String, Object> body = kafkaClient.envelopeFields(event);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = jsonMapper.convertValue(event.data(), Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawOptions = (List<Map<String, Object>>) data.get("options");
        rawOptions.get(unknownIndex).put("saleStatus", "NEW_OPTION_STATUS");
        body.put("data", data);

        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, event.correlationId(), event.eventType(),
            event.eventId().toString(), kafkaClient.toJson(body)));
        return event.eventId().toString();
    }

    @Test
    @DisplayName("커뮤니티가 받지 않는 종류는 건너뛰고 DLT로 보내지 않는다")
    void skipsOtherType() {
        String eventId = CommunityKafkaTestClient.newEventId();

        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, "1", "stock.deducted", eventId, "커뮤니티가 읽지 않는 본문"));

        assertThat(kafkaClient.deadLetters(TOPIC, eventId)).isEmpty();
    }

    @Test
    @DisplayName("eventType 헤더가 없으면 재시도 없이 DLT(파티션 0)에 1건 보낸다")
    void missingEventTypeToDeadLetter() {
        String eventId = CommunityKafkaTestClient.newEventId();

        RecordMetadata sent = kafkaClient.send(TOPIC, "1", null, eventId, "{}");

        AsyncWait.until(CommunityKafkaTestClient.TIMEOUT, () -> !kafkaClient.deadLetters(TOPIC, eventId).isEmpty());
        assertThat(kafkaClient.deadLetters(TOPIC, eventId)).singleElement().satisfies(deadLetter -> {
            assertThat(deadLetter.partition()).isZero();
            assertThat(deadLetter.value()).isEqualTo("{}");
            assertThat(deadLetter.timestamp() - sent.timestamp()).isLessThan(RETRY_BACKOFF_TOTAL_MS);
        });
    }

    @Test
    @DisplayName("저장이 실패하면 1초 간격 3번 재시도한 뒤 DLT에 1건 보내고, DLT 로그에는 본문 없이 eventId · 키만 남긴다")
    void processingFailureRetriedThenDeadLetter(CapturedOutput output) {
        long productId = newProductId();
        String marker = "상품_" + UUID.randomUUID();
        jdbcTemplate.execute("alter table community.replica_product add constraint " + BREAK_REPLICA
            + " check (product_id <> " + productId + ") not valid");
        var event = new ProductChangedEvent(UUID.randomUUID(), OCCURRED_AT, data(productId, marker,
            ProductStatus.ON_SALE));

        RecordMetadata sent = kafkaClient.send(TOPIC, event);

        String eventId = event.eventId().toString();
        AsyncWait.until(CommunityKafkaTestClient.TIMEOUT, () -> !kafkaClient.deadLetters(TOPIC, eventId).isEmpty());
        assertThat(kafkaClient.deadLetters(TOPIC, eventId)).singleElement().satisfies(deadLetter -> {
            assertThat(deadLetter.partition()).isZero();
            assertThat(deadLetter.key()).isEqualTo(String.valueOf(productId));
            assertThat(deadLetter.timestamp() - sent.timestamp()).isGreaterThanOrEqualTo(RETRY_BACKOFF_TOTAL_MS);
        });
        assertThat(rowCount(productId)).isZero();
        // 프레임워크(Hibernate)의 SQL 오류 로그는 이 테스트 대상이 아니다. 커뮤니티가 남기는 DLT 줄만 본다
        assertThat(output.getOut().lines().filter(line -> line.contains("DLT로 보냅니다")))
            .singleElement().satisfies(line -> assertThat(line)
                .contains("eventId=" + eventId, "key=" + productId).doesNotContain(marker));
    }

    private Map<String, Object> stored(long productId) {
        return jdbcTemplate.queryForMap("select name, thumbnail_url, display_status, source_occurred_at, updated_at "
            + "from community.replica_product where product_id = ?", productId);
    }

    private int rowCount(long productId) {
        return jdbcTemplate.queryForObject("select count(*) from community.replica_product where product_id = ?",
            Integer.class, productId);
    }

    private static long newProductId() {
        return ThreadLocalRandom.current().nextLong(1_000_000, Long.MAX_VALUE);
    }

    private static ProductChangedEvent changed(long productId, ProductStatus status, LocalDateTime occurredAt) {
        return new ProductChangedEvent(UUID.randomUUID(), occurredAt, data(productId, "상품 A", status));
    }

    private static ProductChangedEvent.Data data(long productId, String name, ProductStatus status) {
        return new ProductChangedEvent.Data(productId, 2L, name, 3L, status, "https://img/1.png",
            OCCURRED_AT.minusDays(1), false, List.of(new ProductChangedEvent.Option(productId, "색상: 네이비", "110",
                25_000L, null, null, false, OptionSaleStatus.ON_SALE)));
    }
}
