package com.bbcc.kidly.boundedcontext.delivery.out.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * 택배 조회 서킷브레이커 설정(#708)이 order.yml 기본값으로 묶이고, 서킷브레이커는 빈으로 두지 않는다(포트 구현 안에서 만든다).
 */
@IntegrationTest
class DeliveryTrackerCircuitBreakerPropertiesIntegrationTest {

    @Autowired
    private DeliveryTrackerCircuitBreakerProperties properties;

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("기본값: 창 20 · 최소 10 · 실패율 50 · 느린 호출 5초 · 느린 비율 50 · 열림 1분 · 반열림 3회")
    void defaults() {
        assertThat(properties).isEqualTo(new DeliveryTrackerCircuitBreakerProperties(
            20, 10, 50, Duration.ofSeconds(5), 50, Duration.ofMinutes(1), 3));
    }

    @Test
    @DisplayName("Resilience4j 빈(레지스트리 · 서킷브레이커)이 하나도 없다(전역 설정 · 자동 설정 없음)")
    void noResilience4jBeans() {
        assertThat(context.getBeanDefinitionNames())
            .noneMatch(name -> context.getType(name) != null
                && context.getType(name).getName().startsWith("io.github.resilience4j"));
    }
}
