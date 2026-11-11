package com.bbcc.kidly.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final int REDIS_PORT = 6379;

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg18-trixie").asCompatibleSubstituteFor("postgres"));
    }

    // AI 답 저장소다. 서버(deploy/docker-compose.yml)와 같은 이미지를 쓴다
    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7.4.12-alpine")).withExposedPorts(REDIS_PORT);
    }
}
