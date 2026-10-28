package com.bbcc.kidly.shared.seller.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SellerEventsTest {

    private static final UUID EVENT_ID = UUID.fromString("6f1c2e4a-0b7d-4c1e-9a55-2d3f8e7b1a90");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 9, 30);

    @Test
    @DisplayName("판매자 변경 이벤트는 seller.changed이고 발행 도메인은 seller, 대상은 판매자다")
    void sellerChanged() {
        var event = new SellerChangedEvent(EVENT_ID, NOW,
            new SellerChangedEvent.Data(2L, "테스트상점", SellerStatus.ACTIVE));

        assertThat(event.eventType()).isEqualTo("seller.changed");
        assertThat(event.producer()).isEqualTo("seller");
        assertThat(event.aggregateId()).isEqualTo("2");
        assertThat(event.correlationId()).isEqualTo("2");
    }
}
