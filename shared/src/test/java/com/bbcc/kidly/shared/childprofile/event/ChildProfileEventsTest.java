package com.bbcc.kidly.shared.childprofile.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChildProfileEventsTest {

    private static final UUID EVENT_ID = UUID.fromString("6f1c2e4a-0b7d-4c1e-9a55-2d3f8e7b1a90");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 9, 30);

    @Test
    @DisplayName("자녀 변경 이벤트는 child.changed이고 발행 도메인은 childprofile, 대상은 자녀다")
    void childChanged() {
        var event = new ChildChangedEvent(EVENT_ID, NOW, new ChildChangedEvent.Data(10L, 1L,
            new BigDecimal("115.0"), new BigDecimal("20.0"), Gender.MALE, LocalDate.of(2019, 1, 1),
            BodyType.NORMAL, PreferredFit.LOOSE, false));

        assertThat(event.eventType()).isEqualTo("child.changed");
        assertThat(event.producer()).isEqualTo("childprofile");
        assertThat(event.aggregateId()).isEqualTo("10");
        assertThat(event.correlationId()).isEqualTo("10");
    }
}
