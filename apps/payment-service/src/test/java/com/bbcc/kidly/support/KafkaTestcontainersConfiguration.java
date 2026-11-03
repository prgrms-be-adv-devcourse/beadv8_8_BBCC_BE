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
 * 이 서비스에는 회원 · 주문 · 정산 도메인이 없으므로, 결제가 받는 세 토픽을 보내는 쪽과 같은 모양(파티션 3 · 복제 1)으로 여기서 선언한다.
 * 빈 이름은 결제 도메인이 선언하는 토픽 · DLT 토픽 빈 이름과 겹치지 않게 지었다.
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
    NewTopic receivedMemberEventsTopic() {
        return eventsTopic("kidly.member.events");
    }

    @Bean
    NewTopic receivedOrderEventsTopic() {
        return eventsTopic("kidly.order.events");
    }

    @Bean
    NewTopic receivedSettlementEventsTopic() {
        return eventsTopic("kidly.settlement.events");
    }

    private static NewTopic eventsTopic(String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }
}
