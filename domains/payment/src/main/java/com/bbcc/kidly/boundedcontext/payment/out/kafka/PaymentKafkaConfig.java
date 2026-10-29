package com.bbcc.kidly.boundedcontext.payment.out.kafka;

import com.bbcc.kidly.boundedcontext.payment.app.port.PaymentEventSender;
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
import tools.jackson.databind.json.JsonMapper;

/**
 * 결제가 보내는 토픽과 결제 전용 KafkaTemplate.
 * 템플릿은 Boot 프로듀서 설정(주소 등)을 그대로 쓰고 직렬화 · 대기 시간만 덮어쓴다.
 * 빈으로 등록하지 않으므로 Boot 기본 KafkaTemplate과 앱 spring.kafka 설정은 그대로다.
 */
@Configuration
class PaymentKafkaConfig {

    // 브로커가 없으면 send가 메타데이터를 기다리며 기본 60초 멈춘다. 커밋 뒤 요청 스레드에서 보내므로 짧게 둔다
    static final int MAX_BLOCK_MS = 3000;

    // 파티션 수는 만든 뒤 바꾸지 않는다(늘리면 키가 들어가는 파티션이 바뀌어 순서가 깨진다)
    @Bean
    NewTopic paymentEventsTopic() {
        return TopicBuilder.name(KafkaPaymentEventSender.TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    PaymentEventSender paymentEventSender(ProducerFactory<String, String> producerFactory, JsonMapper jsonMapper) {
        KafkaTemplate<String, String> kafkaTemplate = new KafkaTemplate<>(producerFactory, Map.of(
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.MAX_BLOCK_MS_CONFIG, MAX_BLOCK_MS));
        // 기본 LoggingProducerListener는 실패 로그에 본문을 찍는다. 실패는 KafkaPaymentEventSender가 본문 없이 남긴다
        kafkaTemplate.setProducerListener(new ProducerListener<>() {
        });
        return new KafkaPaymentEventSender(kafkaTemplate, jsonMapper);
    }
}
