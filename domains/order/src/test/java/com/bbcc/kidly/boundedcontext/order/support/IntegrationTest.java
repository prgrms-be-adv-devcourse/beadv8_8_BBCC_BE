package com.bbcc.kidly.boundedcontext.order.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import com.bbcc.kidly.boundedcontext.order.OrderTestApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 주문 통합 테스트(#616): 주문 테스트 앱(주문 · common만) + 실제 DB(Testcontainers). 테스트가 끝나면 orders 표를 비운다.
 * 모든 통합 테스트가 같은 설정을 써야 컨텍스트가 재사용되므로 설정을 추가하지 않는다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("integration")
@SpringBootTest(classes = OrderTestApplication.class)
@Import(OrderTestcontainers.class)
@ActiveProfiles("test")
@ExtendWith(OrderDatabaseCleanerExtension.class)
public @interface IntegrationTest {
}
