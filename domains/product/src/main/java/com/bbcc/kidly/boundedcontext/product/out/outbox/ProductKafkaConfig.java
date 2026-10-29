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

/**
 * 상품이 보내는 토픽과 상품 전용 KafkaTemplate.
 * 템플릿은 Boot 프로듀서 설정(주소 등)을 그대로 쓰고 직렬화만 덮어쓴다.
 * 빈으로 등록하지 않으므로 Boot 기본 KafkaTemplate과 앱 spring.kafka 설정은 그대로다.
 */
@Configuration
class ProductKafkaConfig {

    // 브로커 응답을 기다리는 시간. 이 시간이 지나면 보내지 못한 것으로 본다
    static final Duration SEND_TIMEOUT = Duration.ofSeconds(15);

    // 파티션 수는 만든 뒤 바꾸지 않는다(늘리면 키가 들어가는 파티션이 바뀌어 순서가 깨진다)
    @Bean
    NewTopic productEventsTopic() {
        return TopicBuilder.name(JpaProductEventOutbox.TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    ProductEventSender productEventSender(ProducerFactory<String, String> producerFactory) {
        KafkaTemplate<String, String> kafkaTemplate = new KafkaTemplate<>(producerFactory, Map.of(
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
        return new KafkaProductEventSender(kafkaTemplate, SEND_TIMEOUT);
    }
}
