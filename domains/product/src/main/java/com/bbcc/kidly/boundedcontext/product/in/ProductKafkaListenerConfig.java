package com.bbcc.kidly.boundedcontext.product.in;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.bbcc.kidly.global.error.BusinessException;
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
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.core.JacksonException;

@Slf4j
@Configuration
public class ProductKafkaListenerConfig {

    public static final String SELLER_EVENTS = "kidly.seller.events";
    public static final String ORDER_EVENTS = "kidly.order.events";
    public static final String GROUP = "kidly.product";
    public static final String AUTO_STARTUP = "${kidly.product.kafka.consumer.enabled:true}";
    private static final String DLT_SUFFIX = ".product.dlt";

    @Bean
    NewTopic productSellerEventsDeadLetterTopic() {
        return TopicBuilder.name(SELLER_EVENTS + DLT_SUFFIX).partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic productOrderEventsDeadLetterTopic() {
        return TopicBuilder.name(ORDER_EVENTS + DLT_SUFFIX).partitions(1).replicas(1).build();
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<Object, Object> productKafkaListenerContainerFactory(
        ConcurrentKafkaListenerContainerFactoryConfigurer configurer, ConsumerFactory<Object, Object> consumerFactory,
        KafkaTemplate<String, String> kafkaTemplate) {
        var factory = new ConcurrentKafkaListenerContainerFactory<Object, Object>();
        configurer.configure(factory, consumerFactory);
        factory.getContainerProperties().setAckMode(AckMode.RECORD);
        factory.setCommonErrorHandler(errorHandler(kafkaTemplate));
        return factory;
    }

    private static DefaultErrorHandler errorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        var deadLetter = new DeadLetterPublishingRecoverer(kafkaTemplate,
            (record, e) -> new TopicPartition(record.topic() + DLT_SUFFIX, -1));
        var errorHandler = new DefaultErrorHandler((record, e) -> {
            logDeadLetter(record, e);
            deadLetter.accept(record, e);
        }, new FixedBackOff(1000L, 3));
        errorHandler.addNotRetryableExceptions(JacksonException.class, NullPointerException.class,
            IllegalArgumentException.class, IllegalStateException.class, BusinessException.class);
        return errorHandler;
    }

    private static void logDeadLetter(ConsumerRecord<?, ?> record, Exception e) {
        var exception = NestedExceptionUtils.getMostSpecificCause(e).getClass().getName();
        log.error("처리하지 못한 이벤트를 DLT로 보냅니다. topic={} eventType={} eventId={} key={} exception={}",
            record.topic(), header(record, "eventType"), header(record, "eventId"), record.key(), exception);
    }

    public static String header(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), UTF_8);
    }
}
