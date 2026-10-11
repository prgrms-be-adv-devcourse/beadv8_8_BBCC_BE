package com.bbcc.kidly.support;

import com.bbcc.kidly.TestcontainersConfiguration;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 통합 테스트: 스프링 컨텍스트 + 실제 DB(Testcontainers). 테스트가 끝나면 모든 테이블을 비운다.
 * 모든 통합 테스트가 같은 설정을 써야 컨텍스트가 재사용되므로 설정을 추가하지 않는다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("integration")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ExtendWith(DatabaseCleanerExtension.class)
public @interface IntegrationTest {
}
