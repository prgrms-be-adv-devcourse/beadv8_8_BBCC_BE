package com.bbcc.kidly;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18"));
    }

    // 운영(docker-compose)과 같은 이미지 · 같은 설정: 토픽 자동 생성을 끈다(보내는 쪽이 NewTopic으로 선언).
    // @ServiceConnection은 Kafka 연결 정보 팩토리(spring-boot-kafka)가 클래스패스에 생기면 붙인다(#534). 그전엔 주소를 컨테이너에서 읽는다
    @Bean
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("apache/kafka:4.2.2"))
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");
    }
}
