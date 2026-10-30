package com.bbcc.kidly.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 통합 테스트: 스프링 컨텍스트 + 실제 Elasticsearch(Testcontainers, nori). 테스트가 끝나면 색인을 비운다.
 * 모든 통합 테스트가 같은 설정을 써야 컨텍스트가 재사용되므로 설정을 추가하지 않는다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("integration")
@SpringBootTest
@Import({ElasticsearchTestcontainersConfiguration.class, SearchIndexes.class})
@ActiveProfiles("test")
@ExtendWith(SearchIndexCleanerExtension.class)
public @interface IntegrationTest {
}
