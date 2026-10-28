package com.bbcc.kidly.global.eventpublisher;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.core.annotation.AliasFor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.transaction.annotation.Transactional;

/**
 * 도메인 간 이벤트를 Kafka에서 받는 메서드에 붙인다. {@link DomainEventListener}를 이것으로 바꾼다.
 * 토픽은 이벤트의 eventType, groupId는 받는 도메인 이름이다.
 * <pre>
 * {@code @KafkaDomainEventListener(topics = "member.registered", groupId = "payment")}
 * public void on(MemberRegisteredEvent event)
 * </pre>
 * 처리는 한 트랜잭션에서 하고, 실패하면 그 트랜잭션만 롤백된 뒤 몇 번 다시 시도하고 ERROR 로그를 남긴다 (KafkaListenerConfig).
 * 같은 메시지를 두 번 이상 받을 수 있으므로 eventId 또는 업무 상태로 중복을 거른다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@KafkaListener
@Transactional
public @interface KafkaDomainEventListener {

    @AliasFor(annotation = KafkaListener.class, attribute = "topics")
    String[] topics();

    @AliasFor(annotation = KafkaListener.class, attribute = "groupId")
    String groupId();
}
