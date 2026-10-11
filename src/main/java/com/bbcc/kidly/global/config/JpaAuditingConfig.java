package com.bbcc.kidly.global.config;

import java.time.Clock;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// 메인 클래스에 두면 @WebMvcTest 같은 슬라이스 테스트가 JPA 없이 뜨다 실패하므로 분리한다
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    // 시각은 Clock에서 가져와 테스트에서 고정할 수 있게 한다
    @Bean
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(clock.instant());
    }
}
