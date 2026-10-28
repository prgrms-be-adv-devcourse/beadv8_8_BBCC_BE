package com.bbcc.kidly.global.eventpublisher;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EventPublisherTest {

    private static final UUID EVENT_ID = UUID.fromString("6f1c2e4a-0b7d-4c1e-9a55-2d3f8e7b1a90");

    private final List<Object> published = new ArrayList<>();
    private final EventPublisher publisher = new EventPublisher(published::add);

    @Test
    @DisplayName("도메인 이벤트를 스프링 이벤트로 발행한다")
    void publishesDomainEventAsSpringEvent() {
        TestEvent event = new TestEvent(EVENT_ID, LocalDateTime.of(2026, 10, 11, 0, 0), new TestEvent.Data(1L));

        publisher.publish(event);

        assertThat(published).containsExactly(event);
    }

    private record TestEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

        record Data(Long id) {
        }

        @Override
        public String aggregateId() {
            return String.valueOf(data.id());
        }
    }
}
