package com.bbcc.kidly.boundedcontext.order.support;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 주문 Kafka 테스트(@KafkaIntegrationTest)만 쓰는 Kafka 컨테이너(#616). 앱 테스트 · 운영(docker-compose)과 같은 이미지 · 같은 설정:
 * 토픽 자동 생성을 끈다. 주문 테스트 앱에는 보내는 도메인(상품 · 판매자 · 자녀 · 결제)이 없어 그 토픽을 선언하는 코드도 없으므로,
 * 주문이 받는 토픽을 보내는 쪽과 같은 모양(파티션 3 · 복제 1)으로 여기서 선언한다. 주문 토픽 · DLT는 주문 코드가 선언한다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class OrderKafkaTestcontainers {

    private static final int PARTITIONS = 3;

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("apache/kafka:4.2.2"))
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");
    }

    @Bean
    NewTopic productEventsTopic() {
        return topic("kidly.product.events");
    }

    @Bean
    NewTopic sellerEventsTopic() {
        return topic("kidly.seller.events");
    }

    @Bean
    NewTopic childProfileEventsTopic() {
        return topic("kidly.childprofile.events");
    }

    @Bean
    NewTopic paymentEventsTopic() {
        return topic("kidly.payment.events");
    }

    private static NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(PARTITIONS).replicas(1).build();
    }
}
