package com.bbcc.kidly.boundedcontext.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 커뮤니티 통합 · 기능 테스트의 PostgreSQL 컨테이너. 앱 테스트와 같은 이미지.
 * Kafka 컨테이너는 Kafka 수신 테스트를 추가할 때(#658) 따로 둔다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class CommunityTestcontainers {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18"));
    }
}
