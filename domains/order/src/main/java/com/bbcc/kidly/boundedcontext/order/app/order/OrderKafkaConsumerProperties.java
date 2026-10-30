package com.bbcc.kidly.boundedcontext.order.app.order;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 주문 Kafka 수신 스위치(#541). 기본값은 order.yml 한 곳(켜짐)에만 둔다.
 * 켜져 있으면 주문 리스너 컨테이너가 앱 spring.kafka.listener 설정대로 시작하고 Kafka로만 받는다.
 * 꺼져 있으면 컨테이너가 자동 시작하지 않고(리스너 빈은 만든다) Spring 이벤트로 받는다(SpringEventReceiver, 장애 때 되돌리기).
 * 결제 마감 만료도 이 값으로 결제 이벤트 대기량 확인 여부를 정하므로(PaymentExpiryGate, #594) app에 둔다.
 *
 * @param enabled Kafka로 받을지
 */
@ConfigurationProperties("kidly.order.kafka.consumer")
public record OrderKafkaConsumerProperties(boolean enabled) {
}
