package com.bbcc.kidly.boundedcontext.recommend.app.ai;

import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 추천 AI 지표 설정이다. Spring Boot가 MeterFilter 빈을 지표 레지스트리에 붙인다.
 */
@Configuration
public class RecommendAiMetricsConfig {

    @Bean
    public MeterFilter recommendAiCallTimeBuckets() {
        return RecommendAiMetrics.callTimeBuckets();
    }
}
