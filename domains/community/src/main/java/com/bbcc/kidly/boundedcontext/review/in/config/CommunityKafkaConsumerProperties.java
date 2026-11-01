package com.bbcc.kidly.boundedcontext.review.in.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 커뮤니티 Kafka 수신 스위치. 기본값은 community.yml 한 곳(켜짐)에만 둔다.
 * 켜져 있으면 커뮤니티 리스너 컨테이너가 앱 spring.kafka.listener 설정대로 시작하고 Kafka로만 받는다.
 * 꺼져 있으면 컨테이너가 자동 시작하지 않고(리스너 빈은 만든다) Spring 이벤트로 받는다(CommunitySpringEventReceiver, 장애 때 되돌리기).
 *
 * @param enabled Kafka로 받을지
 */
@ConfigurationProperties("kidly.community.kafka.consumer")
public record CommunityKafkaConsumerProperties(boolean enabled) {
}
