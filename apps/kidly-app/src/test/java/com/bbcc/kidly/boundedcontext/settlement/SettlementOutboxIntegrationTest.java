package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.settlement.app.AdminSettlementService;
import com.bbcc.kidly.boundedcontext.settlement.app.SettlementPayoutRetryScheduler;
import com.bbcc.kidly.boundedcontext.settlement.app.SettlementRunService;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.settlement.event.SettlementPayoutRequestedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 정산이 발행한 지급 요청을 업무와 같은 트랜잭션에서 아웃박스에 저장하는지 본다.
 * 지급 요청을 발행하는 세 경로(정산 실행 · 관리자 재요청 · 자동 재요청)와 롤백 · 중복 발행을 확인한다.
 */
@IntegrationTest
class SettlementOutboxIntegrationTest {

    private static final Long ADMIN_ID = 3L;
    private static final LocalDate CUTOFF_ON = LocalDate.now().withDayOfMonth(1);
    private static final LocalDateTime CUTOFF_AT = CUTOFF_ON.atStartOfDay();
    private static final String COUNT_BY_EVENT_TYPE =
        "select count(*) from settlement.settlement_outbox where event_type = 'settlement.payout-requested'";

    @Autowired
    private SettlementRunService settlementRunService;

    @Autowired
    private AdminSettlementService adminSettlementService;

    @Autowired
    private SettlementPayoutRetryScheduler retryScheduler;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private SettlementItemRepository settlementItemRepository;

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    @DisplayName("정산을 실행하면 정산서마다 지급 요청이 정산서 ID 키로 아웃박스에 한 행씩 쌓인다")
    void recordOnRun() {
        settlementItemRepository.save(SettlementFixture.item(201L, 10L, 10_000L, CUTOFF_AT.minusDays(1)));
        settlementItemRepository.save(SettlementFixture.item(202L, 11L, 20_000L, CUTOFF_AT.minusDays(2)));

        settlementRunService.runManually(CUTOFF_ON, ADMIN_ID);

        List<Long> settlementIds = jdbcTemplate.queryForList("select id from settlement.settlement", Long.class);
        assertThat(settlementIds).hasSize(2);
        assertThat(jdbcTemplate.queryForList("select message_key from settlement.settlement_outbox "
            + "where topic = 'kidly.settlement.events' and event_type = 'settlement.payout-requested'",
            String.class))
            .containsExactlyInAnyOrderElementsOf(settlementIds.stream().map(String::valueOf).toList());
        assertThat(jdbcTemplate.queryForObject("select count(*) from settlement.settlement_outbox "
            + "where sent_at is null and attempt_count = 0", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("관리자가 지급을 재요청하면 요청마다 한 행이 쌓인다")
    void recordOnAdminRetry() {
        var settlement = settlementRepository.save(SettlementFixture.settlement(10_000L));

        adminSettlementService.retryPayout(settlement.getId());

        assertThat(outboxCount()).isOne();
        assertThat(jdbcTemplate.queryForObject("select message_key from settlement.settlement_outbox", String.class))
            .isEqualTo(String.valueOf(settlement.getId()));
    }

    @Test
    @DisplayName("자동 재요청이 지급을 다시 요청하면 한 행이 쌓인다")
    void recordOnAutoRetry() {
        var settlement = settlementRepository.save(SettlementFixture.settlement(10_000L));

        int retried = retryScheduler.retryStuckPayouts(SettlementFixture.NOW.plusHours(1));

        assertThat(retried).isOne();
        assertThat(outboxCount()).isOne();
        assertThat(jdbcTemplate.queryForObject("select message_key from settlement.settlement_outbox", String.class))
            .isEqualTo(String.valueOf(settlement.getId()));
    }

    @Test
    @DisplayName("업무가 롤백되면 메시지도 남지 않는다")
    void rolledBack() {
        SettlementPayoutRequestedEvent event = payoutRequested();

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(event);
            status.setRollbackOnly();
        });

        assertThat(countByEventId(event.eventId())).isZero();
    }

    @Test
    @DisplayName("같은 이벤트를 다시 발행해도 메시지는 하나다. 업무는 실패하지 않는다")
    void sameEventOnce() {
        SettlementPayoutRequestedEvent event = payoutRequested();

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));

        assertThat(countByEventId(event.eventId())).isOne();
    }

    @Test
    @DisplayName("저장된 본문은 공통 칸 7개와 data다")
    void payload() {
        SettlementPayoutRequestedEvent event = payoutRequested();

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));

        Map<String, Object> row = jdbcTemplate.queryForMap("select event_type, topic, message_key, payload "
            + "from settlement.settlement_outbox where event_id = ?", event.eventId());
        assertThat(row).containsEntry("event_type", "settlement.payout-requested")
            .containsEntry("topic", "kidly.settlement.events")
            .containsEntry("message_key", "31");
        JsonNode body = jsonMapper.readTree((String) row.get("payload"));
        assertThat(body.propertyNames()).containsExactly("eventId", "eventType", "schemaVersion", "occurredAt",
            "producer", "aggregateId", "correlationId", "data");
        assertThat(body.get("producer").asString()).isEqualTo("settlement");
        assertThat(body.get("correlationId").asString()).isEqualTo("31");
        assertThat(body.get("data").get("settlementId").asLong()).isEqualTo(31L);
        assertThat(body.get("data").get("amount").asLong()).isEqualTo(270_000L);
    }

    private int outboxCount() {
        return jdbcTemplate.queryForObject(COUNT_BY_EVENT_TYPE, Integer.class);
    }

    private int countByEventId(UUID eventId) {
        return jdbcTemplate.queryForObject("select count(*) from settlement.settlement_outbox where event_id = ?",
            Integer.class, eventId);
    }

    private static SettlementPayoutRequestedEvent payoutRequested() {
        return new SettlementPayoutRequestedEvent(UUID.randomUUID(), LocalDateTime.of(2026, 11, 1, 0, 0),
            new SettlementPayoutRequestedEvent.Data(31L, 10L, 270_000L, LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31)));
    }
}
