package com.bbcc.kidly.boundedcontext.order.in.event;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 주문 Kafka 수신 스위치(#541). 기본값은 order.yml 한 곳(꺼짐)에만 둔다.
 * 꺼져 있으면 주문 리스너 컨테이너가 자동 시작하지 않는다(리스너 빈은 만든다). 켜져 있으면 앱 spring.kafka.listener 설정을 따른다.
 *
 * @param enabled Kafka로 받을지
 */
@ConfigurationProperties("kidly.order.kafka.consumer")
public record OrderKafkaConsumerProperties(boolean enabled) {
}
