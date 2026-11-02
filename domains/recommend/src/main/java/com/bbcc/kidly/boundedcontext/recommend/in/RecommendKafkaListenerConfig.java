package com.bbcc.kidly.boundedcontext.recommend.in;

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

/**
 * 추천이 Kafka로 받을 때 쓰는 컨테이너 팩토리와 DLT를 만든다.
 * 복사본 리스너(in.replica)와 구매 기록 리스너(in.purchase)가 함께 쓴다.
 * 팩토리는 Boot configurer로 앱 spring.kafka.listener 설정(테스트의 auto-startup: false 포함)을 그대로 받고,
 * 한 건씩 처리 완료를 표시하며 실패하면 1초 간격으로 3번 더 시도한 뒤 DLT로 보낸다.
 * 읽지 못한 메시지와 다시 해도 같은 결과인 예외는 재시도 없이 보낸다.
 * 에러 처리기는 빈으로 등록하지 않으므로 Boot 기본 팩토리와 다른 도메인에 영향이 없다.
 * DLT로 보낼 때는 Boot 기본 KafkaTemplate을 쓴다. 전송 실패는 앱 설정이 본문 없이 토픽 · 키 · eventType · eventId로 남긴다.
 */
@Slf4j
@Configuration
public class RecommendKafkaListenerConfig {

    private static final String DLT_SUFFIX = ".recommend.dlt";

    @Bean
    NewTopic recommendSellerEventsDeadLetterTopic() {
        return deadLetterTopic("kidly.seller.events");
    }

    @Bean
    NewTopic recommendChildProfileEventsDeadLetterTopic() {
        return deadLetterTopic("kidly.childprofile.events");
    }

    @Bean
    NewTopic recommendProductEventsDeadLetterTopic() {
        return deadLetterTopic("kidly.product.events");
    }

    @Bean
    NewTopic recommendOrderEventsDeadLetterTopic() {
        return deadLetterTopic("kidly.order.events");
    }

    // configurer가 <Object, Object> 팩토리만 받으므로 타입 인자를 맞춘다. 키 · 본문은 앱 설정대로 String이다
    @Bean
    ConcurrentKafkaListenerContainerFactory<Object, Object> recommendKafkaListenerContainerFactory(
        ConcurrentKafkaListenerContainerFactoryConfigurer configurer, ConsumerFactory<Object, Object> consumerFactory,
        KafkaTemplate<String, String> kafkaTemplate) {
        var factory = new ConcurrentKafkaListenerContainerFactory<Object, Object>();
        configurer.configure(factory, consumerFactory);
        factory.getContainerProperties().setAckMode(AckMode.RECORD);
        factory.setCommonErrorHandler(errorHandler(kafkaTemplate));
        return factory;
    }

    private static NewTopic deadLetterTopic(String topic) {
        return TopicBuilder.name(topic + DLT_SUFFIX).partitions(1).replicas(1).build();
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

    // 본문(자녀 키 · 몸무게 등이 들어 있을 수 있다)과 예외 메시지(읽지 못한 값이 들어 있다)는 남기지 않는다
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
