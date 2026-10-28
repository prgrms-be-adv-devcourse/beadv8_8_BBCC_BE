package com.bbcc.kidly.global.eventpublisher;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.ProducerListener;

/**
 * Kafka로 보내는 쪽 공통 설정. 직렬화 설정은 kafka.yml에 있다.
 */
@Configuration
public class KafkaProducerConfig {

    // Boot 기본(LoggingProducerListener)은 전송 실패 로그에 이벤트 내용(가입 이메일 등)을 그대로 찍는다.
    // 실패는 KafkaDomainEventSender가 eventType · eventId · key만 남기므로 기본 로그는 끈다
    @Bean
    ProducerListener<Object, Object> kafkaProducerListener() {
        return new ProducerListener<>() {
        };
    }
}
