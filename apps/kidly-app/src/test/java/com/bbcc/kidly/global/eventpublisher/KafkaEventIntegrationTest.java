package com.bbcc.kidly.global.eventpublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.bbcc.kidly.support.IntegrationTest;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class KafkaEventIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    // 컨텍스트를 다른 테스트와 같이 쓰므로 받은 메시지를 구분할 ID를 테스트마다 새로 쓴다
    private static final AtomicLong IDS = new AtomicLong(1_000);

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private KafkaTestListener listener;

    @Test
    @DisplayName("커밋되면 eventType 토픽에 correlationId 키로 보내고, 받는 쪽은 같은 이벤트로 읽는다")
    void sendsCommittedEvent() {
        KafkaTestEvent event = KafkaTestEvent.of(IDS.incrementAndGet(), IDS.incrementAndGet(), false);

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));

        ConsumerRecord<String, KafkaTestEvent> received = awaitReceived(event);
        assertThat(received.topic()).isEqualTo(KafkaTestEvent.TOPIC);
        assertThat(received.key()).isEqualTo(event.correlationId());
        assertThat(received.value()).isEqualTo(event);
    }

    @Test
    @DisplayName("롤백되면 보내지 않는다")
    void doesNotSendRolledBackEvent() {
        long orderId = IDS.incrementAndGet();
        KafkaTestEvent rolledBack = KafkaTestEvent.of(IDS.incrementAndGet(), orderId, false);
        KafkaTestEvent committed = KafkaTestEvent.of(IDS.incrementAndGet(), orderId, false);

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(rolledBack);
            status.setRollbackOnly();
        });
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(committed));

        // 같은 키라 같은 파티션에 순서대로 들어가므로, 뒤에 보낸 것을 받았으면 앞의 것은 오지 않은 것이다
        awaitReceived(committed);
        assertThat(listener.attempts(rolledBack.eventId())).isZero();
    }

    @Test
    @DisplayName("받는 쪽이 실패하면 정해진 횟수만큼 다시 시도한 뒤 다음 메시지로 넘어간다")
    void retriesThenSkipsFailedEvent() {
        long orderId = IDS.incrementAndGet();
        KafkaTestEvent failing = KafkaTestEvent.of(IDS.incrementAndGet(), orderId, true);
        KafkaTestEvent next = KafkaTestEvent.of(IDS.incrementAndGet(), orderId, false);

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(failing));
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(next));

        awaitReceived(next);
        assertThat(listener.attempts(failing.eventId())).isEqualTo(1 + (int) KafkaListenerConfig.RETRY_COUNT);
    }

    private ConsumerRecord<String, KafkaTestEvent> awaitReceived(KafkaTestEvent event) {
        return await().atMost(TIMEOUT).until(
            () -> listener.received().stream()
                .filter(consumerRecord -> consumerRecord.value().eventId().equals(event.eventId()))
                .findFirst().orElse(null),
            consumerRecord -> consumerRecord != null);
    }
}
