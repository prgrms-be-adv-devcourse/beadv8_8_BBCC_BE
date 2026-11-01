package com.bbcc.kidly.boundedcontext.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 주문 통합 · 기능 테스트의 PostgreSQL 컨테이너(#616). 앱 테스트와 같은 이미지.
 * Kafka 컨테이너는 Kafka가 필요한 테스트만 띄운다(OrderKafkaTestcontainers, @KafkaIntegrationTest).
 */
@TestConfiguration(proxyBeanMethods = false)
public class OrderTestcontainers {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18"));
    }
}
