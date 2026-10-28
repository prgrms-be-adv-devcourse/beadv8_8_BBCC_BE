package com.bbcc.kidly.global.eventpublisher;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * Kafka 통합 테스트용 리스너. 테스트 클래스 안의 중첩 클래스는 스캔에서 빠지므로 최상위 클래스로 둔다.
 * 모든 통합 · 기능 테스트 컨텍스트에 등록되므로 테스트 전용 토픽만 받는다.
 */
@Component
public class KafkaTestListener {

    private final List<ConsumerRecord<String, KafkaTestEvent>> received = new CopyOnWriteArrayList<>();
    private final Map<UUID, AtomicInteger> attempts = new ConcurrentHashMap<>();

    // 컨텍스트가 뜰 때 토픽을 만들어 둔다. 없으면 첫 send가 자동 생성을 기다리다 max.block.ms를 넘겨 실패할 수 있다
    @Bean
    NewTopic kafkaTestTopic() {
        return new NewTopic(KafkaTestEvent.TOPIC, 1, (short) 1);
    }

    @KafkaDomainEventListener(topics = KafkaTestEvent.TOPIC, groupId = "kafka-test")
    public void on(ConsumerRecord<String, KafkaTestEvent> consumerRecord) {
        KafkaTestEvent event = consumerRecord.value();
        attempts.computeIfAbsent(event.eventId(), id -> new AtomicInteger()).incrementAndGet();
        if (event.data().fail()) {
            throw new IllegalStateException("테스트 실패");
        }
        received.add(consumerRecord);
    }

    public List<ConsumerRecord<String, KafkaTestEvent>> received() {
        return received;
    }

    public int attempts(UUID eventId) {
        AtomicInteger count = attempts.get(eventId);
        return count == null ? 0 : count.get();
    }
}
