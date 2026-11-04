package com.bbcc.kidly.support;

import java.util.TimeZone;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
// 서버는 compose의 TZ: Asia/Seoul로 돈다. 테스트 JVM도 같은 기본 시간대로 맞춰 시각 비교가 PC마다 달라지지 않게 한다(kidly-app과 같다)
public class TestcontainersConfiguration {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18"));
    }
}
