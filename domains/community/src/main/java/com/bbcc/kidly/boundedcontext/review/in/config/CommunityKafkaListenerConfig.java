package com.bbcc.kidly.boundedcontext.review.in.config;

import com.bbcc.kidly.boundedcontext.review.in.event.CommunityKafkaMessages;
import com.bbcc.kidly.boundedcontext.review.in.event.CommunityLogContextRecordInterceptor;
import com.bbcc.kidly.global.error.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
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
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ProducerListener;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.core.JacksonException;

/**
 * 커뮤니티가 Kafka로 받을 때 쓰는 컨테이너 팩토리와 DLT. 구독 그룹은 kidly.community. 주문 팩토리의 경량판이다(DLT 보관 · 재처리 없음).
 * 팩토리는 Boot configurer로 앱 spring.kafka.listener 설정을 그대로 받고 커뮤니티 값만 덮어쓴다:
 * <ul>
 *   <li>한 건씩 처리 완료를 표시한다(RECORD)</li>
 *   <li>처음 읽는 그룹은 처음부터 읽는다(earliest, 앱 기본 latest를 이 팩토리의 컨테이너에만 덮어씀). 다시 읽어도 결과가 1회분이라 안전하다</li>
 *   <li>스위치(kidly.community.kafka.consumer.enabled)가 꺼져 있으면 컨테이너를 자동 시작하지 않는다</li>
 *   <li>실패하면 1초 간격으로 3번 더 시도한 뒤 {토픽}.community.dlt로 보낸다. 다시 해도 같은 예외 · 읽기 실패는 재시도 없이 보낸다</li>
 *   <li>메시지 하나를 처리하는 동안 로그 MDC에 eventId · eventType · correlationId(키)를 둔다(에러 처리기 로그까지)</li>
 * </ul>
 * 에러 처리기 · DLT 템플릿은 빈으로 등록하지 않으므로 Boot 기본 팩토리 · KafkaTemplate과 다른 도메인에 영향이 없다.
 */
@Slf4j
@Configuration
public class CommunityKafkaListenerConfig {

    public static final String GROUP = "kidly.community";
    public static final String FACTORY = "communityKafkaListenerContainerFactory";
    public static final String PRODUCT_EVENTS = "kidly.product.events";

    private static final String DEAD_LETTER_SUFFIX = ".community.dlt";
    private static final long RETRY_INTERVAL_MS = 1000L;
    private static final long RETRY_ATTEMPTS = 3L;

    @Bean
    NewTopic communityProductEventsDeadLetterTopic() {
        return TopicBuilder.name(PRODUCT_EVENTS + DEAD_LETTER_SUFFIX).partitions(1).replicas(1).build();
    }

    // configurer가 <Object, Object> 팩토리만 받으므로 타입 인자를 맞춘다. 키 · 본문은 앱 설정대로 String이다
    @Bean(FACTORY)
    ConcurrentKafkaListenerContainerFactory<Object, Object> communityKafkaListenerContainerFactory(
        ConcurrentKafkaListenerContainerFactoryConfigurer configurer, ConsumerFactory<Object, Object> consumerFactory,
        ProducerFactory<String, String> producerFactory, CommunityKafkaConsumerProperties properties) {
        var factory = new ConcurrentKafkaListenerContainerFactory<Object, Object>();
        configurer.configure(factory, consumerFactory);
        factory.getContainerProperties().setAckMode(AckMode.RECORD);
        // 팩토리의 kafkaConsumerProperties는 만들어지는 컨테이너로 복사되지 않아 컨테이너마다 넣는다(ConsumerFactory는 그대로)
        factory.setContainerCustomizer(container -> container.getContainerProperties().getKafkaConsumerProperties()
            .setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"));
        // 꺼짐만 강제한다. 켜져 있으면 앱 설정(테스트는 false)을 따른다
        if (!properties.enabled()) {
            factory.setAutoStartup(false);
        }
        factory.setCommonErrorHandler(errorHandler(new DeadLetterPublishingRecoverer(
            deadLetterTemplate(producerFactory), (record, e) -> deadLetterPartition(record))));
        factory.setRecordInterceptor(new CommunityLogContextRecordInterceptor());
        return factory;
    }

    /**
     * 다시 해도 같은 결과인 예외(업무 규칙 · 잘못된 값 · 상태 · null)와 읽기 실패(Jackson)는 재시도하지 않는다.
     */
    private static DefaultErrorHandler errorHandler(ConsumerRecordRecoverer deadLetter) {
        var errorHandler = new DefaultErrorHandler((record, e) -> {
            logDeadLetter(record, e);
            deadLetter.accept(record, e);
        }, new FixedBackOff(RETRY_INTERVAL_MS, RETRY_ATTEMPTS));
        errorHandler.addNotRetryableExceptions(BusinessException.class, IllegalArgumentException.class,
            IllegalStateException.class, NullPointerException.class, JacksonException.class);
        return errorHandler;
    }

    /**
     * DLT는 파티션이 1개라 원래 메시지의 파티션 번호(1 · 2)를 쓰면 보내지 못한다. 항상 0으로 보낸다.
     */
    private static TopicPartition deadLetterPartition(ConsumerRecord<?, ?> record) {
        return new TopicPartition(record.topic() + DEAD_LETTER_SUFFIX, 0);
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
        String exception = NestedExceptionUtils.getMostSpecificCause(e).getClass().getName();
        log.error("처리하지 못한 이벤트를 DLT로 보냅니다. topic={} eventType={} eventId={} key={} exception={}",
            record.topic(), CommunityKafkaMessages.header(record, CommunityKafkaMessages.EVENT_TYPE),
            CommunityKafkaMessages.header(record, CommunityKafkaMessages.EVENT_ID), record.key(), exception);
    }
}
