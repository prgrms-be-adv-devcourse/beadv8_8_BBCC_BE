package com.bbcc.kidly.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// 메인 클래스에 두면 @WebMvcTest 같은 슬라이스 테스트가 JPA 없이 뜨다 실패하므로 분리한다
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
