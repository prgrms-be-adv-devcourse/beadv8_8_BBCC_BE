package com.bbcc.kidly.shared.auth.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChildAccountChangedEventTest {

    @Test
    void usesAccountIdAsKey() {
        var event = new ChildAccountChangedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new ChildAccountChangedEvent.Data(12L, 34L, 56L, false));

        assertThat(event.eventType()).isEqualTo("child-account.changed");
        assertThat(event.producer()).isEqualTo("auth");
        assertThat(event.aggregateId()).isEqualTo("12");
        assertThat(event.correlationId()).isEqualTo("12");
    }
}
