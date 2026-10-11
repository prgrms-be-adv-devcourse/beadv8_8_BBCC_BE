package com.bbcc.kidly.global.eventpublisher;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EventPublisherTest {

    private final List<Object> published = new ArrayList<>();
    private final EventPublisher publisher = new EventPublisher(published::add);

    @Test
    @DisplayName("도메인 이벤트를 스프링 이벤트로 발행한다")
    void publishesDomainEventAsSpringEvent() {
        TestEvent event = new TestEvent(1L, Instant.parse("2026-10-11T00:00:00Z"));

        publisher.publish(event);

        assertThat(published).containsExactly(event);
    }

    private record TestEvent(Long id, Instant occurredAt) {
    }
}
