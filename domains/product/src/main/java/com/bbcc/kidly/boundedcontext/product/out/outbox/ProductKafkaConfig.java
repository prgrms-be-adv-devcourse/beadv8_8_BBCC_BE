package com.bbcc.kidly.boundedcontext.product.out.outbox;

import com.bbcc.kidly.boundedcontext.product.app.port.outbox.ProductEventSender;
import java.time.Duration;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.ProducerListener;

@Configuration
class ProductKafkaConfig {

    static final String CLIENT_ID = "kidly-product-outbox";

    static final int MAX_BLOCK_MS = 5000;

    // delivery.timeout.ms가 linger.ms + request.timeout.ms보다 작으면 첫 send에서 프로듀서를 만들 때 설정 오류가 나므로 함께 줄인다
    static final int REQUEST_TIMEOUT_MS = 5000;

    static final int DELIVERY_TIMEOUT_MS = 10_000;

    static final int LINGER_MS = 0;

    static final Duration SEND_TIMEOUT = Duration.ofMillis(DELIVERY_TIMEOUT_MS + 2000);

    @Bean
    NewTopic productEventsTopic() {
        return TopicBuilder.name(JpaProductEventOutbox.TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    ProductEventSender productEventSender(ProducerFactory<String, String> producerFactory) {
        var kafkaTemplate = new KafkaTemplate<>(producerFactory, Map.of(
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.CLIENT_ID_CONFIG, CLIENT_ID,
            ProducerConfig.MAX_BLOCK_MS_CONFIG, MAX_BLOCK_MS,
            ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, REQUEST_TIMEOUT_MS,
            ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, DELIVERY_TIMEOUT_MS,
            ProducerConfig.LINGER_MS_CONFIG, LINGER_MS));
        kafkaTemplate.setProducerListener(new ProducerListener<>() {
        });
        return new KafkaProductEventSender(kafkaTemplate, SEND_TIMEOUT);
    }
}
