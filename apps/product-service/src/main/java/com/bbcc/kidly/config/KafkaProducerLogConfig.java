package com.bbcc.kidly.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.ProducerListener;

@Configuration(proxyBeanMethods = false)
class KafkaProducerLogConfig {

    @Bean
    ProducerListener<Object, Object> kafkaProducerListener() {
        return new KafkaSendFailureLogger();
    }
}
