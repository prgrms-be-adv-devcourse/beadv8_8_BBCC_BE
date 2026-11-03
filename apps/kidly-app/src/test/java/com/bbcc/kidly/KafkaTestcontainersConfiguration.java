package com.bbcc.kidly;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Kafka가 필요한 테스트(@KafkaIntegrationTest)만 쓰는 Kafka 컨테이너.
 * 운영(docker-compose)과 같은 이미지 · 같은 설정: 토픽 자동 생성을 끈다(보내는 쪽이 NewTopic으로 선언).
 * 앱의 Kafka 주소(spring.kafka.bootstrap-servers)는 이 컨테이너를 가리킨다.
 * 이 앱에는 회원 · 판매자 도메인이 없으므로(회원 서비스), 이 앱이 받는 두 토픽은 보내는 쪽과 같은 모양(파티션 3 · 복제 1)으로 여기서 선언한다.
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
    NewTopic memberEventsTopic() {
        return TopicBuilder.name("kidly.member.events").partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic sellerEventsTopic() {
        return TopicBuilder.name("kidly.seller.events").partitions(3).replicas(1).build();
    }
}
