package com.bbcc.kidly.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.LoggingProducerListener;

/**
 * Kafka 전송 실패 로그에 메시지 본문을 남기지 않는다. 본문에는 가입 이메일, 아이 키 · 몸무게 같은 개인정보가 들어 있다.
 * Boot 기본 빈(kafkaProducerListener)은 키와 본문을 앞 100자까지 남기므로 이 빈으로 바꾼다. 앱에 하나뿐인 빈이라 앱에 둔다.
 */
@Configuration(proxyBeanMethods = false)
class KafkaProducerLogConfig {

    @Bean
    LoggingProducerListener<Object, Object> kafkaProducerListener() {
        LoggingProducerListener<Object, Object> listener = new LoggingProducerListener<>();
        listener.setIncludeContents(false);
        return listener;
    }
}
