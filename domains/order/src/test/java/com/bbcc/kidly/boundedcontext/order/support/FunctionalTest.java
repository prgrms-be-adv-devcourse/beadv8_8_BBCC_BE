package com.bbcc.kidly.boundedcontext.order.support;

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
 * 주문 기능 테스트(#616): 주문 테스트 앱을 실제 포트로 띄우고 RestTestClient로 HTTP 요청을 보낸다. 보안은 common 설정 그대로이고
 * 토큰은 TestTokens가 만든다. 테스트가 끝나면 orders 표를 비운다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("functional")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(OrderTestcontainers.class)
@ActiveProfiles("test")
@ExtendWith(OrderDatabaseCleanerExtension.class)
public @interface FunctionalTest {
}
