package com.bbcc.kidly.boundedcontext.payment.out.kafka;

import com.bbcc.kidly.boundedcontext.payment.app.port.PaymentEventSender;
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
 * 결제가 보내는 토픽과 결제 전용 KafkaTemplate.
 * 템플릿은 Boot 프로듀서 설정(주소 등)을 그대로 쓰고 직렬화 · 전달 보장 · 대기 시간만 덮어쓴다.
 * 빈으로 등록하지 않으므로 Boot 기본 KafkaTemplate과 앱 spring.kafka 설정은 그대로다.
 * 대기 시간을 줄이는 이유: 릴레이는 스케줄러 스레드(앱 전체 1개) · DB 트랜잭션 · 릴레이 잠금을 쥔 채 보내고 기다린다.
 * 기본값(max.block 60초 · delivery 120초)이면 브로커가 죽었을 때 한 주기가 몇 분씩 다른 스케줄러와 DB 연결을 붙잡는다.
 */
@Configuration
class PaymentKafkaConfig {

    // 브로커가 응답하지 않거나 토픽 정보가 없을 때 send가 기다리는 시간
    static final int MAX_BLOCK_MS = 3000;

    // 브로커의 요청 응답을 기다리는 시간. delivery.timeout.ms는 linger.ms + request.timeout.ms 이상이어야 한다
    static final int REQUEST_TIMEOUT_MS = 5000;

    // send가 돌아온 뒤 전송 성공 · 실패가 정해지기까지의 최대 시간
    static final int DELIVERY_TIMEOUT_MS = 10_000;

    // 메시지 하나씩 보내고 응답을 기다리므로 배치로 모을 일이 없다
    static final int LINGER_MS = 0;

    // 메타데이터 대기(max.block) + 전달 제한(delivery.timeout). 이 안에 끝나지 않으면 실패로 본다
    static final Duration SEND_TIMEOUT = Duration.ofMillis(MAX_BLOCK_MS + DELIVERY_TIMEOUT_MS);

    // 파티션 수는 만든 뒤 바꾸지 않는다(늘리면 키가 들어가는 파티션이 바뀌어 순서가 깨진다)
    @Bean
    NewTopic paymentEventsTopic() {
        return TopicBuilder.name(PaymentOutboxWriter.TOPIC).partitions(3).replicas(1).build();
    }

    // acks=all · 멱등은 앱 spring.kafka에도 있지만, 결제 서비스를 따로 띄울 때도 같게 여기서 정한다
    @Bean
    PaymentEventSender paymentEventSender(ProducerFactory<String, String> producerFactory) {
        KafkaTemplate<String, String> kafkaTemplate = new KafkaTemplate<>(producerFactory, Map.of(
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.ACKS_CONFIG, "all",
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true,
            ProducerConfig.MAX_BLOCK_MS_CONFIG, MAX_BLOCK_MS,
            ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, REQUEST_TIMEOUT_MS,
            ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, DELIVERY_TIMEOUT_MS,
            ProducerConfig.LINGER_MS_CONFIG, LINGER_MS));
        // 기본 LoggingProducerListener는 실패 로그에 본문을 찍는다. 실패는 릴레이가 본문 없이 남긴다
        kafkaTemplate.setProducerListener(new ProducerListener<>() {
        });
        return new KafkaPaymentEventSender(kafkaTemplate, SEND_TIMEOUT);
    }
}
