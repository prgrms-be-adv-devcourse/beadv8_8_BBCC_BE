package com.bbcc.kidly.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 기능 테스트: 실제 포트로 서비스를 띄우고 RestTestClient로 HTTP 요청을 보낸다. 테스트가 끝나면 색인을 비운다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("functional")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import({ElasticsearchTestcontainersConfiguration.class, SearchIndexes.class})
@ActiveProfiles("test")
@ExtendWith(SearchIndexCleanerExtension.class)
public @interface FunctionalTest {
}
