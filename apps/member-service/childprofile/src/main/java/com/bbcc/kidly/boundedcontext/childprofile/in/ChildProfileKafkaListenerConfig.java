package com.bbcc.kidly.boundedcontext.childprofile.in;

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

@Slf4j
@Configuration
class ChildProfileKafkaListenerConfig {

    @Bean
    NewTopic childprofileMemberEventsDeadLetterTopic() {
        return TopicBuilder.name(dlt("kidly.member.events")).partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic childprofileAuthEventsDeadLetterTopic() {
        return TopicBuilder.name(dlt("kidly.auth.events")).partitions(1).replicas(1).build();
    }

    // configurer가 <Object, Object> 팩토리만 받으므로 타입 인자를 맞춘다
    @Bean
    ConcurrentKafkaListenerContainerFactory<Object, Object> childprofileKafkaListenerContainerFactory(
        ConcurrentKafkaListenerContainerFactoryConfigurer configurer, ConsumerFactory<Object, Object> consumerFactory,
        ProducerFactory<String, String> producerFactory) {
        var factory = new ConcurrentKafkaListenerContainerFactory<Object, Object>();
        configurer.configure(factory, consumerFactory);
        factory.getContainerProperties().setAckMode(AckMode.RECORD);
        factory.setCommonErrorHandler(errorHandler(producerFactory));
        return factory;
    }

    private static String dlt(String topic) {
        return topic + ".childprofile.dlt";
    }

    private static DefaultErrorHandler errorHandler(ProducerFactory<String, String> producerFactory) {
        var deadLetter = new DeadLetterPublishingRecoverer(deadLetterTemplate(producerFactory),
            (record, e) -> new TopicPartition(dlt(record.topic()), 0));
        var errorHandler = new DefaultErrorHandler((record, e) -> {
            logDeadLetter(record, e);
            deadLetter.accept(record, e);
        }, new FixedBackOff(1000L, 3));
        errorHandler.addNotRetryableExceptions(JacksonException.class, IllegalArgumentException.class);
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
        log.error("처리하지 못한 이벤트를 DLT로 보냅니다. eventType={} eventId={} key={} exception={}",
            header(record, "eventType"), header(record, "eventId"), record.key(), exception);
    }

    static String header(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), UTF_8);
    }
}
