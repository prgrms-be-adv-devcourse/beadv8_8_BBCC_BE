package com.bbcc.kidly.boundedcontext.search.in;

import static java.nio.charset.StandardCharsets.UTF_8;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.kafka.autoconfigure.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ProducerListener;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.core.JacksonException;

/**
 * 검색이 Kafka로 받을 때 쓰는 컨테이너 팩토리와 DLT(받는 토픽마다 하나).
 * 팩토리는 Boot configurer로 앱 spring.kafka.listener 설정(테스트의 auto-startup: false 포함)을 그대로 받고,
 * 한 건씩 처리 완료를 표시하며 실패하면 1초 간격으로 3번 더 시도한 뒤 DLT로 보낸다(Elasticsearch가 잠깐 응답하지 않는 경우).
 * 다시 해도 같은 실패는 재시도 없이 보낸다: 읽지 못한 본문(JacksonException), 검색 문서로 만들 수 없는 값
 * (옵션 없는 상품 · 필수 값 없음 · eventType 헤더 없음, IllegalArgumentException · NullPointerException).
 * 에러 처리기 · DLT 템플릿은 빈으로 등록하지 않으므로 Boot 기본 팩토리 · KafkaTemplate에 영향이 없다.
 */
@Slf4j
@Configuration
class SearchKafkaListenerConfig {

    static final String PRODUCT_EVENTS = "kidly.product.events";
    static final String SELLER_EVENTS = "kidly.seller.events";
    static final String GROUP = "kidly.search";
    private static final String DLT_SUFFIX = ".search.dlt";

    @Bean
    NewTopic searchProductEventsDeadLetterTopic() {
        return TopicBuilder.name(PRODUCT_EVENTS + DLT_SUFFIX).partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic searchSellerEventsDeadLetterTopic() {
        return TopicBuilder.name(SELLER_EVENTS + DLT_SUFFIX).partitions(1).replicas(1).build();
    }

    // configurer가 <Object, Object> 팩토리만 받으므로 타입 인자를 맞춘다. 키 · 본문은 앱 설정대로 String이다
    @Bean
    ConcurrentKafkaListenerContainerFactory<Object, Object> searchKafkaListenerContainerFactory(
        ConcurrentKafkaListenerContainerFactoryConfigurer configurer, ConsumerFactory<Object, Object> consumerFactory,
        ProducerFactory<String, String> producerFactory) {
        var factory = new ConcurrentKafkaListenerContainerFactory<Object, Object>();
        configurer.configure(factory, consumerFactory);
        factory.getContainerProperties().setAckMode(AckMode.RECORD);
        factory.setCommonErrorHandler(errorHandler(producerFactory));
        return factory;
    }

    // 실패한 메시지는 받은 토픽의 DLT(예: kidly.product.events.search.dlt)로 보낸다
    private static DefaultErrorHandler errorHandler(ProducerFactory<String, String> producerFactory) {
        var deadLetter = new DeadLetterPublishingRecoverer(deadLetterTemplate(producerFactory),
            (record, e) -> new TopicPartition(record.topic() + DLT_SUFFIX, 0));
        var errorHandler = new DefaultErrorHandler((record, e) -> {
            logDeadLetter(record, e);
            deadLetter.accept(record, e);
        }, new FixedBackOff(1000L, 3));
        errorHandler.addNotRetryableExceptions(
            JacksonException.class, IllegalArgumentException.class, NullPointerException.class);
        return errorHandler;
    }

    // Boot 프로듀서 팩토리를 그대로 써서 공용 프로듀서를 빌려 쓰므로 따로 닫을 것이 없다
    private static KafkaTemplate<String, String> deadLetterTemplate(ProducerFactory<String, String> producerFactory) {
        var kafkaTemplate = new KafkaTemplate<>(producerFactory);
        // 기본 LoggingProducerListener는 실패 로그에 본문을 찍는다. 실패는 DeadLetterPublishingRecoverer가 본문 없이 남긴다
        kafkaTemplate.setProducerListener(new ProducerListener<>() {
        });
        return kafkaTemplate;
    }

    // 본문과 예외 메시지(읽지 못한 값이 들어 있다)는 남기지 않는다
    private static void logDeadLetter(ConsumerRecord<?, ?> record, Exception e) {
        var exception = NestedExceptionUtils.getMostSpecificCause(e).getClass().getName();
        log.error("처리하지 못한 이벤트를 DLT로 보냅니다. topic={} eventType={} eventId={} key={} exception={}",
            record.topic(), header(record, "eventType"), header(record, "eventId"), record.key(), exception);
    }

    static String header(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), UTF_8);
    }

    // eventType이 없으면 어떤 메시지인지 알 수 없다. 다시 해도 같으므로 재시도 없이 DLT로 보낸다
    static String eventType(ConsumerRecord<?, ?> record) {
        var eventType = header(record, "eventType");
        if (eventType == null) {
            throw new IllegalArgumentException("eventType 헤더가 없습니다");
        }
        return eventType;
    }
}
