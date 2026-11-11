package com.bbcc.kidly.boundedcontext.support;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 커뮤니티 테스트 앱의 지표 레지스트리. 이 모듈 테스트에는 actuator가 없어 레지스트리 빈이 없으므로 메모리 레지스트리를 준다
 * (앱은 actuator가 Prometheus 레지스트리를 준다). 테스트는 이 레지스트리에서 지표 값을 읽는다.
 */
@Configuration(proxyBeanMethods = false)
public class CommunityTestMeterRegistryConfig {

    @Bean
    @ConditionalOnMissingBean
    MeterRegistry communityTestMeterRegistry() {
        return new SimpleMeterRegistry();
    }
}
