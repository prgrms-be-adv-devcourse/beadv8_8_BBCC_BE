package com.bbcc.kidly.boundedcontext.childprofile.out.outbox;

import com.bbcc.kidly.boundedcontext.childprofile.app.port.outbox.ChildProfileEventSender;
import java.time.Duration;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.ProducerListener;

/**
 * 전용 KafkaTemplate을 빈으로 등록하지 않으므로 Boot 기본 KafkaTemplate과 앱 spring.kafka 설정은 그대로다.
 */
@Configuration
class ChildProfileKafkaConfig {

    static final int MAX_BLOCK_MS = 3000;

    // delivery.timeout.ms가 linger.ms + request.timeout.ms보다 작으면 첫 send에서 프로듀서를 만들 때 설정 오류가 나므로 함께 줄인다
    static final int REQUEST_TIMEOUT_MS = 5000;

    static final int DELIVERY_TIMEOUT_MS = 10_000;

    // 응답은 실패로라도 delivery.timeout.ms 안에 돌아오므로, 그보다 조금 길게 기다려 응답을 놓치지 않게 한다
    static final Duration SEND_TIMEOUT = Duration.ofMillis(DELIVERY_TIMEOUT_MS + 2000);

    @Bean
    NewTopic childProfileEventsTopic() {
        return TopicBuilder.name(JpaChildProfileEventOutbox.TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    ChildProfileEventSender childProfileEventSender(ProducerFactory<String, String> producerFactory) {
        var kafkaTemplate = new KafkaTemplate<>(producerFactory, Map.<String, Object>of(
            ProducerConfig.MAX_BLOCK_MS_CONFIG, MAX_BLOCK_MS,
            ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, REQUEST_TIMEOUT_MS,
            ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, DELIVERY_TIMEOUT_MS));
        // 기본 LoggingProducerListener는 실패 로그에 본문(아이의 키 · 몸무게 등)을 찍는다.
        kafkaTemplate.setProducerListener(new ProducerListener<>() {
        });
        return new KafkaChildProfileEventSender(kafkaTemplate, SEND_TIMEOUT);
    }
}
