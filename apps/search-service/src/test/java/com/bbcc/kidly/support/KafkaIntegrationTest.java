package com.bbcc.kidly.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * Kafka가 필요한 통합 테스트: @IntegrationTest에 Kafka 컨테이너를 더하고, 테스트에서 꺼 둔 토픽 생성과 검색 이벤트 보내기를 켠다.
 * 설정이 달라 스프링 컨텍스트가 @IntegrationTest와 따로 하나 더 뜬다. 설정을 추가하지 않는다.
 * 리스너는 여기서도 저절로 시작하지 않는다. 수신을 확인하는 테스트는 그 안에서 자기 리스너만 켜고 끈다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@IntegrationTest
@Import(KafkaTestcontainersConfiguration.class)
@TestPropertySource(properties = {
    "spring.kafka.admin.auto-create=true",
    "kidly.kafka.producer.enabled=true"
})
public @interface KafkaIntegrationTest {
}
