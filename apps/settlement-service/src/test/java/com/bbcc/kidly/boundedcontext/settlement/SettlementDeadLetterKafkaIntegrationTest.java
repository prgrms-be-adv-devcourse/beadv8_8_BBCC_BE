package com.bbcc.kidly.boundedcontext.settlement;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementDeadLetterService;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementDeadLetters.State;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementDeadLetters.Summary;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRepository;
import com.bbcc.kidly.boundedcontext.settlement.in.SettlementDeadLetterKafkaListener;
import com.bbcc.kidly.boundedcontext.settlement.in.SettlementKafkaListenerTestGuard;
import com.bbcc.kidly.boundedcontext.settlement.in.SettlementPaymentKafkaListener;
import com.bbcc.kidly.shared.payment.event.PaymentPayoutCompletedEvent;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 처리하지 못한 지급 결과가 DLT를 거쳐 DB에 보관되고, 관리자가 원인을 고친 뒤 재처리하면 정산서에 반영되는지 실제 Kafka로 본다.
 * 지급 결과 리스너와 DLT 보관 리스너만 가드에서 꺼내 켠다. DLT에는 앞 테스트가 남긴 메시지도 있으므로 이 테스트의 eventId로 고른다.
 */
@KafkaIntegrationTest
class SettlementDeadLetterKafkaIntegrationTest {

    private static final String PAYMENT_TOPIC = "kidly.payment.events";
    private static final String PAYMENT_DLT = "kidly.payment.events.settlement.dlt";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final long ADMIN_ID = 99L;

    @Autowired
    private SettlementKafkaListenerTestGuard listenerGuard;

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private SettlementDeadLetterService deadLetterService;

    private final long sellerId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    private final List<MessageListenerContainer> listeners = new ArrayList<>();

    @BeforeEach
    void startListeners() {
        createPaymentTopic();
        for (String id : List.of(SettlementPaymentKafkaListener.ID, SettlementDeadLetterKafkaListener.ID)) {
            MessageListenerContainer listener = listenerGuard.listener(id);
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
    @DisplayName("금액이 다른 지급 결과 → DLT DB 보관 → 그대로 재처리하면 실패 기록 → 정산서를 고친 뒤 재처리하면 지급 완료")
    void storesAndReprocessesDeadLetter() throws Exception {
        long settlementId = saveSettlement();
        String eventId = UUID.randomUUID().toString();
        RecordMetadata sent = send(settlementId, eventId, completed(settlementId, 90_001L));

        AsyncWait.until(TIMEOUT, () -> deadLetterCount(eventId) == 1);
        Long id = deadLetterId(eventId);
        assertThat(deadLetterService.list(null, 0, 100).getContent()).extracting(Summary::getId).contains(id);
        Summary stored = deadLetterService.get(id);
        assertThat(stored.getState()).isEqualTo(State.OPEN);
        assertThat(stored.getDltTopic()).isEqualTo(PAYMENT_DLT);
        assertThat(stored.getSourceTopic()).isEqualTo(PAYMENT_TOPIC);
        assertThat(stored.getSourcePartition()).isEqualTo(sent.partition());
        assertThat(stored.getSourceOffset()).isEqualTo(sent.offset());
        assertThat(stored.getMessageKey()).isEqualTo(String.valueOf(settlementId));
        assertThat(stored.getEventType()).isEqualTo("payment.payout-completed");
        assertThat(stored.getFailureType()).isEqualTo(IllegalStateException.class.getName());

        Summary failed = deadLetterService.reprocess(id, ADMIN_ID);
        assertThat(failed.getState()).isEqualTo(State.OPEN);
        assertThat(failed.getReplayCount()).isOne();
        assertThat(failed.getLastReplayedBy()).isEqualTo(ADMIN_ID);
        assertThat(failed.getLastReplayFailureType()).isEqualTo(IllegalStateException.class.getName());
        assertThat(settlementStatus(settlementId)).isEqualTo("COMPLETED");

        // 지급액 = 판매 합계 - 수수료(DB CHECK)를 지키며 정산서를 지급 결과 금액에 맞춘다
        jdbcTemplate.update("update settlement.settlement set total_sales_amount = 100001, settlement_amount = 90001"
            + " where id = ?", settlementId);
        Summary reprocessed = deadLetterService.reprocess(id, ADMIN_ID);
        assertThat(reprocessed.getState()).isEqualTo(State.REPROCESSED);
        assertThat(reprocessed.getReplayCount()).isEqualTo(2);
        assertThat(reprocessed.getLastReplayFailureType()).isNull();
        assertThat(settlementStatus(settlementId)).isEqualTo("PAID");
        assertThat(deadLetterService.list("REPROCESSED", 0, 100).getContent()).extracting(Summary::getId)
            .contains(id);

        // 이미 성공한 기록은 다시 실행하지 않는다
        assertThat(deadLetterService.reprocess(id, ADMIN_ID).getReplayCount()).isEqualTo(2);
        // 실패 · 성공한 재처리만 남고, 실행하지 않은 마지막 요청은 남지 않는다
        assertThat(AdminActions.of(jdbcTemplate, "DEAD_LETTER_REPROCESS", id)).hasSize(2)
            .allSatisfy(row -> assertThat(row.adminId()).isEqualTo(ADMIN_ID));
    }

    @Test
    @DisplayName("감사 기록이 실패하면 재처리 결과(실패 · 성공)도 남지 않아 DLT가 OPEN으로 남고, 다시 재처리하면 결과와 기록이 함께 남는다")
    void auditFailureRollsBackReplayResult() throws Exception {
        long settlementId = saveSettlement();
        String eventId = UUID.randomUUID().toString();
        send(settlementId, eventId, completed(settlementId, 90_001L));
        AsyncWait.until(TIMEOUT, () -> deadLetterCount(eventId) == 1);
        Long id = deadLetterId(eventId);

        // 관리자 ID가 없으면 감사 기록만 NOT NULL 위반으로 실패한다(DLT의 last_replayed_by는 null을 허용한다)
        // 처리기 실패 → 실패 기록 + 감사 기록 실패: 실패 기록도 되돌린다
        assertThatThrownBy(() -> deadLetterService.reprocess(id, null))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(deadLetterService.get(id).getReplayCount()).isZero();

        // 처리기 성공 → 완료 기록 + 감사 기록 실패: 반영(PAID)은 처리기 트랜잭션으로 남고 완료 기록은 되돌린다
        jdbcTemplate.update("update settlement.settlement set total_sales_amount = 100001, settlement_amount = 90001"
            + " where id = ?", settlementId);
        assertThatThrownBy(() -> deadLetterService.reprocess(id, null))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(settlementStatus(settlementId)).isEqualTo("PAID");
        Summary notRecorded = deadLetterService.get(id);
        assertThat(notRecorded.getState()).isEqualTo(State.OPEN);
        assertThat(notRecorded.getReplayCount()).isZero();
        assertThat(AdminActions.of(jdbcTemplate, "DEAD_LETTER_REPROCESS", id)).isEmpty();

        // OPEN이라 다시 재처리할 수 있다. 처리기는 멱등해 이미 PAID면 그대로 두고, 결과와 감사 기록이 함께 남는다
        Summary reprocessed = deadLetterService.reprocess(id, ADMIN_ID);
        assertThat(reprocessed.getState()).isEqualTo(State.REPROCESSED);
        assertThat(reprocessed.getReplayCount()).isOne();
        assertThat(AdminActions.of(jdbcTemplate, "DEAD_LETTER_REPROCESS", id)).hasSize(1);
    }

    private long saveSettlement() {
        return settlementRepository.save(SettlementFixture.settlement(sellerId,
            SettlementFixture.PERIOD_START, SettlementFixture.PERIOD_END)).getId();
    }

    private PaymentPayoutCompletedEvent completed(long settlementId, long amount) {
        return new PaymentPayoutCompletedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new PaymentPayoutCompletedEvent.Data(settlementId, sellerId, amount, 1L, LocalDateTime.now()));
    }

    // 결제 토픽은 결제 서비스가 선언한다. 테스트가 같은 모양(파티션 3)으로 만든다
    private void createPaymentTopic() {
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
            kafka.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(PAYMENT_TOPIC, 3, (short) 1))).all().get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (!(e.getCause() instanceof TopicExistsException)) {
                throw new IllegalStateException("결제 토픽을 만들지 못했습니다", e);
            }
        } catch (Exception e) {
            throw new IllegalStateException("결제 토픽을 만들지 못했습니다", e);
        }
    }

    // 보내는 쪽 본문과 같은 모양: 공통 칸 7개 + data
    private RecordMetadata send(long key, String eventId, PaymentPayoutCompletedEvent event) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> fields = jsonMapper.convertValue(event, Map.class);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", fields.get("eventId"));
        body.put("eventType", event.eventType());
        body.put("schemaVersion", event.schemaVersion());
        body.put("occurredAt", fields.get("occurredAt"));
        body.put("producer", event.producer());
        body.put("aggregateId", event.aggregateId());
        body.put("correlationId", event.correlationId());
        body.put("data", fields.get("data"));
        ProducerRecord<String, String> record = new ProducerRecord<>(PAYMENT_TOPIC, String.valueOf(key),
            jsonMapper.writeValueAsString(body));
        record.headers().add("eventId", eventId.getBytes(UTF_8));
        record.headers().add("eventType", event.eventType().getBytes(UTF_8));
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            return producer.send(record).get(10, TimeUnit.SECONDS);
        }
    }

    private long deadLetterCount(String eventId) {
        return jdbcTemplate.queryForObject("select count(*) from settlement.settlement_dead_letter where event_id = ?",
            Long.class, eventId);
    }

    private Long deadLetterId(String eventId) {
        return jdbcTemplate.queryForObject("select id from settlement.settlement_dead_letter where event_id = ?",
            Long.class, eventId);
    }

    private String settlementStatus(long settlementId) {
        return jdbcTemplate.queryForObject("select status from settlement.settlement where id = ?", String.class,
            settlementId);
    }
}
