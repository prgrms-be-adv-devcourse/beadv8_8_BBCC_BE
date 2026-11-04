package com.bbcc.kidly.boundedcontext.settlement.in;

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
 * 정산 DLT를 DB에 보관하는 리스너(SettlementDeadLetterKafkaListener)의 컨테이너 팩토리. 구독 그룹은 kidly.settlement.dlt.
 * 정산 리스너 팩토리(SettlementKafkaListenerConfig)와 달리 DLT로 보내지 않는다(DLT의 DLT는 없다).
 * DB에 저장하기 전에는 처리 완료로 넘기지 않고, 저장 실패는 1초부터 두 배씩 최대 60초 간격으로 끝없이 재시도한다.
 * 처음 읽는 그룹은 처음부터 읽는다(earliest). 같은 DLT 위치는 한 번만 저장하므로 다시 읽어도 안전하다.
 * 에러 처리기는 빈으로 등록하지 않으므로 Boot 기본 팩토리와 다른 도메인에 영향이 없다.
 */
@Configuration
class SettlementDeadLetterKafkaListenerConfig {

    static final String FACTORY = "settlementDeadLetterKafkaListenerContainerFactory";
    static final String GROUP = "kidly.settlement.dlt";
    static final String DLT_SUFFIX = ".settlement.dlt";
    static final String ORDER_EVENTS_DLT = SettlementKafkaListenerConfig.ORDER_EVENTS + DLT_SUFFIX;
    static final String PAYMENT_EVENTS_DLT = SettlementKafkaListenerConfig.PAYMENT_EVENTS + DLT_SUFFIX;
    private static final long MAX_RETRY_INTERVAL_MS = 60_000L;

    // configurer가 <Object, Object> 팩토리만 받으므로 타입 인자를 맞춘다. 키 · 본문은 앱 설정대로 String이다
    @Bean(FACTORY)
    ConcurrentKafkaListenerContainerFactory<Object, Object> settlementDeadLetterKafkaListenerContainerFactory(
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
