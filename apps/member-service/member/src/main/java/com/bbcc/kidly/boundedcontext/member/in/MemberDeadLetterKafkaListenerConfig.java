package com.bbcc.kidly.boundedcontext.member.in;

import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.boot.kafka.autoconfigure.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * 에러 처리기는 빈으로 등록하지 않으므로 Boot 기본 팩토리와 다른 도메인에 영향이 없다.
 */
@Configuration
class MemberDeadLetterKafkaListenerConfig {

    static final String FACTORY = "memberDeadLetterKafkaListenerContainerFactory";
    static final String GROUP = "kidly.member.dlt";
    private static final long MAX_RETRY_INTERVAL_MS = 60_000L;

    // configurer가 <Object, Object> 팩토리만 받으므로 타입 인자를 맞춘다. 키 · 본문은 앱 설정대로 String이다.
    @Bean(FACTORY)
    ConcurrentKafkaListenerContainerFactory<Object, Object> memberDeadLetterKafkaListenerContainerFactory(
        ConcurrentKafkaListenerContainerFactoryConfigurer configurer, ConsumerFactory<Object, Object> consumerFactory) {
        var factory = new ConcurrentKafkaListenerContainerFactory<Object, Object>();
        configurer.configure(factory, consumerFactory);
        factory.getContainerProperties().setAckMode(AckMode.RECORD);
        // 팩토리의 kafkaConsumerProperties는 만들어지는 컨테이너로 복사되지 않아 컨테이너마다 넣는다(ConsumerFactory는 그대로)
        factory.setContainerCustomizer(container -> container.getContainerProperties().getKafkaConsumerProperties()
            .setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"));
        var backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxInterval(MAX_RETRY_INTERVAL_MS);
        var errorHandler = new DefaultErrorHandler(backOff);
        // 모든 예외를 재시도한다. 실패 로그는 리스너가 본문 · 예외 메시지 없이 남기므로 처리기 로그는 낮춘다
        errorHandler.setClassifications(Map.of(), true);
        errorHandler.setLogLevel(KafkaException.Level.TRACE);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
