package com.bbcc.kidly.global.eventpublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * DB 없이 트랜잭션 시작·커밋·롤백만 흉내 내는 트랜잭션 매니저로 리스너 실행 시점을 확인한다.
 */
@SpringJUnitConfig(DomainEventListenerTest.Config.class)
class DomainEventListenerTest {

    private static final UUID EVENT_ID = UUID.fromString("6f1c2e4a-0b7d-4c1e-9a55-2d3f8e7b1a90");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 11, 0, 0);

    @Autowired
    private TestPublisher publisher;

    @Autowired
    private TestListener listener;

    @Autowired
    private FakeTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        listener.reset();
        transactionManager.reset();
    }

    @Test
    @DisplayName("발행한 쪽 트랜잭션이 커밋되면 리스너가 실행된다")
    void runsAfterCommit() {
        publisher.publish(new TestEvent(EVENT_ID, 1L, NOW));

        assertThat(listener.received()).containsExactly(new TestEvent(EVENT_ID, 1L, NOW));
    }

    @Test
    @DisplayName("발행한 쪽 트랜잭션이 롤백되면 리스너가 실행되지 않는다")
    void doesNotRunAfterRollback() {
        assertThatThrownBy(() -> publisher.publishAndFail(new TestEvent(EVENT_ID, 1L, NOW)))
            .isInstanceOf(IllegalStateException.class);

        assertThat(listener.received()).isEmpty();
    }

    @Test
    @DisplayName("리스너는 발행한 쪽과 다른 새 트랜잭션에서 실행된다")
    void runsInNewTransaction() {
        publisher.publish(new TestEvent(EVENT_ID, 1L, NOW));

        assertThat(listener.transactionName()).endsWith("TestListener.on");
        assertThat(transactionManager.begun()).isEqualTo(2);
        assertThat(transactionManager.committed()).isEqualTo(2);
    }

    @Test
    @DisplayName("리스너가 실패하면 리스너 트랜잭션만 롤백되고, 예외는 로그만 남고 발행한 쪽으로 전달되지 않는다")
    void listenerFailureDoesNotReachPublisher() {
        listener.failNext();

        assertThatCode(() -> publisher.publish(new TestEvent(EVENT_ID, 1L, NOW))).doesNotThrowAnyException();

        assertThat(transactionManager.committed()).isEqualTo(1);
        assertThat(transactionManager.rolledBack()).isEqualTo(1);
    }

    record TestEvent(UUID eventId, Long id, LocalDateTime occurredAt) implements DomainEvent {

        @Override
        public String aggregateId() {
            return String.valueOf(id);
        }
    }

    static class TestPublisher {

        private final EventPublisher eventPublisher;

        TestPublisher(EventPublisher eventPublisher) {
            this.eventPublisher = eventPublisher;
        }

        @Transactional
        public void publish(TestEvent event) {
            eventPublisher.publish(event);
        }

        @Transactional
        public void publishAndFail(TestEvent event) {
            eventPublisher.publish(event);
            throw new IllegalStateException("발행한 쪽 실패");
        }
    }

    static class TestListener {

        private final List<TestEvent> received = new ArrayList<>();
        private String transactionName;
        private boolean fail;

        @DomainEventListener
        public void on(TestEvent event) {
            transactionName = TransactionSynchronizationManager.getCurrentTransactionName();
            if (fail) {
                throw new IllegalStateException("리스너 실패");
            }
            received.add(event);
        }

        List<TestEvent> received() {
            return received;
        }

        String transactionName() {
            return transactionName;
        }

        void failNext() {
            fail = true;
        }

        void reset() {
            received.clear();
            transactionName = null;
            fail = false;
        }
    }

    static class FakeTransactionManager extends AbstractPlatformTransactionManager {

        private final ThreadLocal<Boolean> active = ThreadLocal.withInitial(() -> false);
        private int begun;
        private int committed;
        private int rolledBack;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return active.get();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            active.set(true);
            begun++;
        }

        @Override
        protected Object doSuspend(Object transaction) {
            active.set(false);
            return Boolean.TRUE;
        }

        @Override
        protected void doResume(Object transaction, Object suspendedResources) {
            active.set(true);
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            committed++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rolledBack++;
        }

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            active.set(false);
        }

        int begun() {
            return begun;
        }

        int committed() {
            return committed;
        }

        int rolledBack() {
            return rolledBack;
        }

        void reset() {
            begun = 0;
            committed = 0;
            rolledBack = 0;
        }
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {

        @Bean
        FakeTransactionManager transactionManager() {
            return new FakeTransactionManager();
        }

        @Bean
        EventPublisher eventPublisher(ApplicationEventPublisher applicationEventPublisher) {
            return new EventPublisher(applicationEventPublisher);
        }

        @Bean
        TestPublisher testPublisher(EventPublisher eventPublisher) {
            return new TestPublisher(eventPublisher);
        }

        @Bean
        TestListener testListener() {
            return new TestListener();
        }
    }
}
