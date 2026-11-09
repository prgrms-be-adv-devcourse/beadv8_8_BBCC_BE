package com.bbcc.kidly.boundedcontext.delivery.out.config;

import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryStatusChecker;
import com.bbcc.kidly.boundedcontext.delivery.out.tracker.DeliveryTrackerChecker;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 택배 조회 포트 구현(out/tracker)을 만든다(#651 배치: 빈 설정 · 연결 설정 값은 out/config).
 * 설정(DeliveryTrackerProperties · DeliveryTrackerCircuitBreakerProperties)은 common의 @ConfigurationPropertiesScan이 등록한다.
 */
@Configuration
class DeliveryTrackerConfig {

    @Bean
    DeliveryStatusChecker deliveryStatusChecker(DeliveryTrackerProperties properties,
        DeliveryTrackerCircuitBreakerProperties circuitBreaker, MeterRegistry registry) {
        // 타임아웃은 여기서 정한다. 클라이언트는 받은 빌더를 그대로 써서 테스트에서 가짜 서버(MockRestServiceServer)를 붙일 수 있다
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        // shippedAt은 LocalDateTime.now()(JVM 기본 시간대)로 기록되므로 같은 시간대로 바꾼다
        // 서킷브레이커(#708)는 빈으로 두지 않고 이 포트 구현 안에서 만든다(전역 resilience4j 설정 · 레지스트리 없음)
        DeliveryTrackerChecker checker = DeliveryTrackerChecker.create(properties, circuitBreaker,
            RestClient.builder().requestFactory(requestFactory), ZoneId.systemDefault(), Clock.systemDefaultZone());
        // 서킷 상태 게이지(#1067, resilience4j 지표 연결 모듈 없이 직접)
        checker.bindCircuitState(registry);
        return checker;
    }
}
