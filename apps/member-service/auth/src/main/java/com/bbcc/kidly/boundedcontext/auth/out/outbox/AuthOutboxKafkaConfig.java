package com.bbcc.kidly.boundedcontext.auth.out.outbox;

import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.ProducerListener;

@Configuration
class AuthOutboxKafkaConfig {
    @Bean
    @ConditionalOnProperty(name = "kidly.kafka.producer.enabled", havingValue = "true", matchIfMissing = true)
    KafkaAuthOutboxSender authOutboxSender(ProducerFactory<String, String> producerFactory) {
        var template = new KafkaTemplate<>(producerFactory, Map.<String, Object>of(
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.MAX_BLOCK_MS_CONFIG, 3000,
            ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000,
            ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 10000));
        template.setProducerListener(new ProducerListener<>() { });
        return new KafkaAuthOutboxSender(template);
    }
}
