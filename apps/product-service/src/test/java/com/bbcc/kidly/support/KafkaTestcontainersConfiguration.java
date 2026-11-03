package com.bbcc.kidly.support;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Kafka가 필요한 테스트(@KafkaIntegrationTest)만 쓰는 Kafka 컨테이너. kidly-app과 같은 이미지 · 설정(토픽 자동 생성 끔)이다.
 * 상품이 받는 판매자 · 주문 토픽은 보내는 도메인이 선언하므로 상품은 선언하지 않는다.
 * 그런데 리스너가 파티션을 배정받아야 테스트가 시작하므로, 이 테스트 설정이 두 토픽을 만든다.
 * 상품 이벤트 토픽(kidly.product.events)은 상품 도메인이 스스로 선언한다.
 * 빈 이름은 상품 도메인이 선언하는 토픽 빈 이름과 겹치지 않게 지었다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class KafkaTestcontainersConfiguration {

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("apache/kafka:4.2.2"))
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");
    }

    @Bean
    NewTopic receivedSellerEventsTopic() {
        return eventsTopic("kidly.seller.events");
    }

    @Bean
    NewTopic receivedOrderEventsTopic() {
        return eventsTopic("kidly.order.events");
    }

    private static NewTopic eventsTopic(String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }
}
