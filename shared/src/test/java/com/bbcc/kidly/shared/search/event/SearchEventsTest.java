package com.bbcc.kidly.shared.search.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SearchEventsTest {

    private static final UUID EVENT_ID = UUID.fromString("0c9a4f3e-5b2d-4e8a-9f61-7d2c1b3a4e50");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 21, 0);

    @Test
    @DisplayName("검색 이벤트는 search.performed이고 발행 도메인은 search, 대상 · 순서 키는 검색한 회원이다")
    void searchPerformed() {
        var event = new SearchPerformedEvent(EVENT_ID, NOW,
            new SearchPerformedEvent.Data(7L, "원피스", 3L, Map.of("COLOR", List.of(3L)), 12L));

        assertThat(event.eventType()).isEqualTo("search.performed");
        assertThat(event.producer()).isEqualTo("search");
        assertThat(event.aggregateId()).isEqualTo("7");
        assertThat(event.correlationId()).isEqualTo("7");
    }
}
