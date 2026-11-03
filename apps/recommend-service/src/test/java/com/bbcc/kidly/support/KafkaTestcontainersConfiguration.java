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
 * 추천은 받기만 해서 판매자 · 자녀 · 상품 · 주문 · 검색 토픽을 선언하지 않는다(보내는 도메인이 선언한다).
 * 그런데 리스너 5개가 모두 파티션을 배정받아야 테스트가 시작하므로, 이 테스트 설정이 다섯 토픽을 만든다.
 * 빈 이름은 추천 도메인이 선언하는 DLT 토픽 빈 이름과 겹치지 않게 지었다.
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
    NewTopic receivedChildProfileEventsTopic() {
        return eventsTopic("kidly.childprofile.events");
    }

    @Bean
    NewTopic receivedProductEventsTopic() {
        return eventsTopic("kidly.product.events");
    }

    @Bean
    NewTopic receivedOrderEventsTopic() {
        return eventsTopic("kidly.order.events");
    }

    @Bean
    NewTopic receivedSearchEventsTopic() {
        return eventsTopic("kidly.search.events");
    }

    private static NewTopic eventsTopic(String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }
}
