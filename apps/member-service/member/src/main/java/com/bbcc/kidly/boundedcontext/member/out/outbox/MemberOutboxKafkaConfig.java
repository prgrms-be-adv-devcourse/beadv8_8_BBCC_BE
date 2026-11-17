package com.bbcc.kidly.boundedcontext.member.out.outbox;

import com.bbcc.kidly.boundedcontext.member.app.port.outbox.MemberOutboxSender;
import java.time.Duration;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.ProducerListener;

/**
 * 회원 아웃박스 릴레이가 쓰는 동기 전송기를 회원 전용 KafkaTemplate으로 만든다.
 * 템플릿은 Boot 프로듀서 설정(주소 · 직렬화 등)을 그대로 쓰고 대기 시간만 덮어쓴다.
 * 빈으로 등록하지 않으므로 Boot 기본 KafkaTemplate과 앱 spring.kafka 설정은 그대로다.
 */
@Configuration
class MemberOutboxKafkaConfig {

    static final String TOPIC = "kidly.member.events";

    // 파티션 수는 만든 뒤 바꾸지 않는다(늘리면 키가 들어가는 파티션이 바뀌어 순서가 깨진다)
    @Bean
    NewTopic memberEventsTopic() {
        return TopicBuilder.name(TOPIC).partitions(3).replicas(1).build();
    }

    // 브로커가 응답하지 않거나 토픽 정보가 없을 때 send가 기다리는 시간이다. 기본 60초는 메시지 하나에 너무 길다
    static final int MAX_BLOCK_MS = 3000;

    // 브로커의 요청 응답을 기다리는 시간이다.
    // delivery.timeout.ms가 linger.ms + request.timeout.ms보다 작으면 첫 send에서 프로듀서를 만들 때 설정 오류가 나므로 함께 줄인다
    static final int REQUEST_TIMEOUT_MS = 5000;

    // send가 돌아온 뒤 전송 성공 · 실패가 정해지기까지의 최대 시간이다. 기본 120초 동안 응답을 기다리지 않게 줄인다
    static final int DELIVERY_TIMEOUT_MS = 10_000;

    // 메시지 하나씩 보내고 응답을 기다리므로 배치로 모을 일이 없다. 기본값(5ms)만큼 메시지마다 늦어지지 않게 0으로 둔다
    static final int LINGER_MS = 0;

    // 응답은 실패로라도 delivery.timeout.ms 안에 돌아오므로, 그보다 조금 길게 기다려 응답을 놓치지 않게 한다
    static final Duration SEND_TIMEOUT = Duration.ofMillis(DELIVERY_TIMEOUT_MS + 2000);

    @Bean
    MemberOutboxSender memberOutboxSender(ProducerFactory<String, String> producerFactory) {
        var kafkaTemplate = new KafkaTemplate<>(producerFactory, Map.<String, Object>of(
            ProducerConfig.MAX_BLOCK_MS_CONFIG, MAX_BLOCK_MS,
            ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, REQUEST_TIMEOUT_MS,
            ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, DELIVERY_TIMEOUT_MS,
            ProducerConfig.LINGER_MS_CONFIG, LINGER_MS));
        // 기본 LoggingProducerListener는 실패 로그에 본문을 찍는다.
        // 실패는 KafkaMemberOutboxSender가 본문 없는 예외로 알린다
        kafkaTemplate.setProducerListener(new ProducerListener<>() {
        });
        return new KafkaMemberOutboxSender(kafkaTemplate, SEND_TIMEOUT);
    }
}
