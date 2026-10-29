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

/**
 * 상품이 보내는 토픽과 상품 전용 KafkaTemplate.
 * 템플릿은 Boot 프로듀서 설정(주소 등)을 그대로 쓰고 직렬화 · 대기 시간만 덮어쓴다.
 * 빈으로 등록하지 않으므로 Boot 기본 KafkaTemplate과 앱 spring.kafka 설정은 그대로다.
 */
@Configuration
class ProductKafkaConfig {

    // 브로커가 응답하지 않거나 토픽 정보가 없을 때 send가 기다리는 시간. 기본 60초는 한 번 보내는 데 너무 길다
    static final int MAX_BLOCK_MS = 5000;

    // 브로커의 요청 응답을 기다리는 시간. delivery.timeout.ms가 이 값보다 작으면 프로듀서를 만들 때 설정 오류가 나므로 함께 줄인다
    static final int REQUEST_TIMEOUT_MS = 5000;

    // send가 돌아온 뒤 전송 성공 · 실패가 정해지기까지의 최대 시간. 기본 120초 동안 응답을 기다리지 않게 줄인다
    static final int DELIVERY_TIMEOUT_MS = 10_000;

    // 응답이 실패로라도 반드시 돌아오는 시간(delivery.timeout.ms)보다 조금 길게 둬서, 응답을 기다리다 시간만 넘기는 일이 없게 한다
    static final Duration SEND_TIMEOUT = Duration.ofMillis(DELIVERY_TIMEOUT_MS + 2000);

    // 파티션 수는 만든 뒤 바꾸지 않는다(늘리면 키가 들어가는 파티션이 바뀌어 순서가 깨진다)
    @Bean
    NewTopic productEventsTopic() {
        return TopicBuilder.name(JpaProductEventOutbox.TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    ProductEventSender productEventSender(ProducerFactory<String, String> producerFactory) {
        KafkaTemplate<String, String> kafkaTemplate = new KafkaTemplate<>(producerFactory, Map.of(
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.MAX_BLOCK_MS_CONFIG, MAX_BLOCK_MS,
            ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, REQUEST_TIMEOUT_MS,
            ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, DELIVERY_TIMEOUT_MS));
        // 기본 LoggingProducerListener는 실패 로그에 본문을 찍는다. 실패는 KafkaProductEventSender가 본문 없는 예외로 알린다
        kafkaTemplate.setProducerListener(new ProducerListener<>() {
        });
        return new KafkaProductEventSender(kafkaTemplate, SEND_TIMEOUT);
    }
}
