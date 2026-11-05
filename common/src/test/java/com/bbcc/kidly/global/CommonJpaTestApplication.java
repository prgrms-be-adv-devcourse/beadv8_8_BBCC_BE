package com.bbcc.kidly.global;

import java.util.TimeZone;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * common의 JPA 통합 테스트(@DataJpaTest)가 찾는 설정. 테스트 엔티티와 실제 PostgreSQL로 공통 JPA 동작을 확인한다.
 * 서비스와 같은 Hibernate 설정은 src/test/resources/application.yml에 둔다.
 */
@SpringBootApplication
class CommonJpaTestApplication {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18"));
    }
}
