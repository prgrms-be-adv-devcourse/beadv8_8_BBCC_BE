package com.bbcc.kidly.boundedcontext.support;

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
 * 커뮤니티 기능 테스트: 커뮤니티 테스트 앱을 실제 포트로 띄우고 RestTestClient로 HTTP 요청을 보낸다. 보안은 common 설정 그대로이고
 * 로그인은 Gateway 헤더(common TestMembers)로 한다. 테스트가 끝나면 community 표를 비운다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("functional")
@SpringBootTest(classes = CommunityTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(CommunityTestcontainers.class)
@ActiveProfiles("test")
@ExtendWith(CommunityDatabaseCleanerExtension.class)
public @interface FunctionalTest {
}
