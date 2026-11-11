package com.bbcc.kidly.boundedcontext.recommend.out.ai;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis 명령 · 연결 대기 시간을 줄인다. 기본값(명령 60초)이면 Redis가 응답하지 않을 때 AI 답 읽기가 요청을 그만큼 붙잡는다.
 * 시간 안에 응답이 없으면 예외가 나고, 부르는 쪽이 저장된 답이 없는 것처럼 AI를 부른다.
 */
@Configuration
public class RecommendRedisClientConfig {

    @Bean
    LettuceClientConfigurationBuilderCustomizer recommendRedisClientCustomizer(
        @Value("${kidly.recommend.ai.store-timeout:PT0.3S}") Duration timeout) {
        return builder -> builder
            .commandTimeout(timeout)
            .clientOptions(ClientOptions.builder()
                .socketOptions(SocketOptions.builder().connectTimeout(timeout).build())
                .build());
    }
}
