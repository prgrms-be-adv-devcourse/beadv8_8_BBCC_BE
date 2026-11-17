package com.bbcc.kidly.gateway.revocation;

import io.lettuce.core.ClientOptions;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RevocationRedisConfig {

    // 끊긴 동안 명령을 쌓아 timeout까지 기다리지 않고 바로 실패시킨다
    @Bean
    LettuceClientOptionsBuilderCustomizer rejectDisconnectedCommands() {
        return builder -> builder.disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS);
    }
}
