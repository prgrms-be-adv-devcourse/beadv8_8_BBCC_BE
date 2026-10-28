package com.bbcc.kidly.global.eventpublisher;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class EventPublisherTest {

    private static final UUID EVENT_ID = UUID.fromString("6f1c2e4a-0b7d-4c1e-9a55-2d3f8e7b1a90");

    private final List<Object> published = new ArrayList<>();
    private final List<DomainEvent> sent = new ArrayList<>();
    private final EventPublisher publisher = new EventPublisher(published::add, sent::add);

    private final TestEvent event =
        new TestEvent(EVENT_ID, LocalDateTime.of(2026, 10, 11, 0, 0), new TestEvent.Data(1L));

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("도메인 이벤트를 스프링 이벤트로 발행한다")
    void publishesDomainEventAsSpringEvent() {
        publisher.publish(event);

        assertThat(published).containsExactly(event);
    }

    @Test
    @DisplayName("트랜잭션이 커밋되면 Kafka로도 보낸다")
    void sendsToKafkaAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();

        publisher.publish(event);
        assertThat(sent).isEmpty();

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        assertThat(sent).containsExactly(event);
    }

    @Test
    @DisplayName("트랜잭션이 롤백되면 Kafka로 보내지 않는다")
    void doesNotSendWhenRolledBack() {
        TransactionSynchronizationManager.initSynchronization();

        publisher.publish(event);
        TransactionSynchronizationManager.getSynchronizations()
            .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(sent).isEmpty();
    }

    @Test
    @DisplayName("트랜잭션 밖에서 발행하면 스프링 이벤트처럼 Kafka로도 보내지 않는다")
    void doesNotSendOutsideTransaction() {
        publisher.publish(event);

        assertThat(sent).isEmpty();
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
