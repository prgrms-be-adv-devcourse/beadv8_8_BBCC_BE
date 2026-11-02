package com.bbcc.kidly.boundedcontext.support;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 커뮤니티 Kafka 테스트(@KafkaIntegrationTest)만 쓰는 Kafka 컨테이너. 앱 테스트 · 운영(docker-compose)과 같은 이미지 · 같은 설정:
 * 토픽 자동 생성을 끈다. 커뮤니티 테스트 앱에는 보내는 도메인(상품)이 없어 그 토픽을 선언하는 코드도 없으므로,
 * 커뮤니티가 받는 토픽을 보내는 쪽과 같은 모양(파티션 3 · 복제 1)으로 여기서 선언한다. DLT는 커뮤니티 코드가 선언한다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class CommunityKafkaTestcontainers {

    private static final int PARTITIONS = 3;

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("apache/kafka:4.2.2"))
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");
    }

    @Bean
    NewTopic productEventsTopic() {
        return TopicBuilder.name("kidly.product.events").partitions(PARTITIONS).replicas(1).build();
    }
}
