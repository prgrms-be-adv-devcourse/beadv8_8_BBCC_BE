package com.bbcc.kidly.boundedcontext.recommend.out.ai;

import io.lettuce.core.ClientOptions.DisconnectedBehavior;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis 연결이 끊긴 동안 명령을 쌓아 두지 않고 바로 거절하게 한다. 기본값이면 다시 연결될 때까지 명령이 기다리다 제한 시간을 넘긴다.
 * 거절되면 부르는 쪽이 저장된 답이 없는 것처럼 AI를 부른다. 명령 · 연결 제한 시간은 {@code spring.data.redis.timeout}으로 정한다.
 * Boot가 정한 다른 연결 옵션은 그대로 둔다.
 */
@Configuration
public class RecommendRedisClientConfig {

    @Bean
    public LettuceClientOptionsBuilderCustomizer recommendRedisClientOptionsCustomizer() {
        return builder -> builder.disconnectedBehavior(DisconnectedBehavior.REJECT_COMMANDS);
    }
}
