package com.bbcc.kidly.boundedcontext.auth.out.outbox;

import com.bbcc.kidly.boundedcontext.auth.domain.outbox.AuthOutboxMessage;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.ProducerListener;

@Configuration
class AuthOutboxKafkaConfig {
    // 파티션 수는 만든 뒤 바꾸지 않는다(늘리면 키가 들어가는 파티션이 바뀌어 순서가 깨진다)
    @Bean
    NewTopic authEventsTopic() {
        return TopicBuilder.name(AuthOutboxMessage.TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    @ConditionalOnProperty(name = "kidly.kafka.producer.enabled", havingValue = "true", matchIfMissing = true)
    KafkaAuthOutboxSender authOutboxSender(ProducerFactory<String, String> producerFactory) {
        var template = new KafkaTemplate<>(producerFactory, Map.<String, Object>of(
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.MAX_BLOCK_MS_CONFIG, 3000,
            ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000,
            ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 10000,
            // 메시지 하나씩 보내고 응답을 기다리므로 배치로 모을 일이 없다. 기본값(5ms)만큼 메시지마다 늦어지지 않게 0으로 둔다
            ProducerConfig.LINGER_MS_CONFIG, 0));
        template.setProducerListener(new ProducerListener<>() { });
        return new KafkaAuthOutboxSender(template);
    }
}
