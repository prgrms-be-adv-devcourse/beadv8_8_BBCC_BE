package com.bbcc.kidly.shared.settlement.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SettlementEventsTest {

    private static final UUID EVENT_ID = UUID.fromString("5c1f0a2e-8d3b-4e7a-9f60-2b4c6d8e0a13");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 3, 0);

    @Test
    @DisplayName("정산 지급 요청 이벤트는 settlement.payout-requested이고 대상 · 흐름은 정산서다")
    void settlementPayoutRequested() {
        SettlementPayoutRequestedEvent event = new SettlementPayoutRequestedEvent(EVENT_ID, NOW,
            new SettlementPayoutRequestedEvent.Data(
                31L, 7L, 270_000L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));

        assertThat(event.eventType()).isEqualTo("settlement.payout-requested");
        assertThat(event.producer()).isEqualTo("settlement");
        assertThat(event.aggregateId()).isEqualTo("31");
        assertThat(event.correlationId()).isEqualTo("31");
    }
}
