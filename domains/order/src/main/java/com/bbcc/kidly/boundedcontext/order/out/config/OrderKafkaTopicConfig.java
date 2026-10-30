package com.bbcc.kidly.boundedcontext.order.out.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * 주문이 보내는 토픽을 선언한다(#540). 브로커의 토픽 자동 생성이 꺼져 있어 보내는 쪽이 만든다(#538).
 * Boot의 KafkaAdmin이 기동 때 없으면 만들고, 있으면 그대로 둔다.
 * 파티션 3: 한 주문의 이벤트는 키(주문 ID)로 같은 파티션에 순서대로 쌓이고, 받는 쪽은 최대 3개까지 나눠 읽는다.
 * 복제 1: 브로커가 한 대(KRaft 단일)라서.
 */
@Configuration
public class OrderKafkaTopicConfig {

    public static final String ORDER_EVENTS = "kidly.order.events";

    @Bean
    NewTopic orderEventsTopic() {
        return TopicBuilder.name(ORDER_EVENTS).partitions(3).replicas(1).build();
    }
}
