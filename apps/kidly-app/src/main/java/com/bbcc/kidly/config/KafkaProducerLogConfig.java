package com.bbcc.kidly.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.ProducerListener;

/**
 * Boot 기본 빈(kafkaProducerListener)을 대신해, Kafka 전송 실패 로그에 토픽 · 키 · eventType · eventId만 남기고
 * 메시지 본문은 남기지 않는다. 본문에는 가입 이메일, 아이 키 · 몸무게 같은 개인정보가 들어 있다.
 * 앱에 하나뿐인 빈이라 앱에 둔다.
 */
@Configuration(proxyBeanMethods = false)
class KafkaProducerLogConfig {

    @Bean
    ProducerListener<Object, Object> kafkaProducerListener() {
        return new KafkaSendFailureLogger();
    }
}
