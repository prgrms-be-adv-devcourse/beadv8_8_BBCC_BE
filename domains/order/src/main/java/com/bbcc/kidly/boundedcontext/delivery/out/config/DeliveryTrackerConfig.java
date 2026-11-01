package com.bbcc.kidly.boundedcontext.delivery.out.config;

import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryStatusChecker;
import com.bbcc.kidly.boundedcontext.delivery.out.tracker.DeliveryTrackerChecker;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 택배 조회 포트 구현(out/tracker)을 만든다(#651 배치: 빈 설정 · 연결 설정 값은 out/config).
 * 설정(DeliveryTrackerProperties)은 common의 @ConfigurationPropertiesScan이 등록한다.
 */
@Configuration
class DeliveryTrackerConfig {

    @Bean
    DeliveryStatusChecker deliveryStatusChecker(DeliveryTrackerProperties properties) {
        // 타임아웃은 여기서 정한다. 클라이언트는 받은 빌더를 그대로 써서 테스트에서 가짜 서버(MockRestServiceServer)를 붙일 수 있다
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        // shippedAt은 LocalDateTime.now()(JVM 기본 시간대)로 기록되므로 같은 시간대로 바꾼다
        return DeliveryTrackerChecker.create(properties, RestClient.builder().requestFactory(requestFactory),
            ZoneId.systemDefault());
    }
}
