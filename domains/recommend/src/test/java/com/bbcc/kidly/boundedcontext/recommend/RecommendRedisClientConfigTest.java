package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.out.ai.RecommendRedisClientConfig;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.ClientOptions.DisconnectedBehavior;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecommendRedisClientConfigTest {

    @Test
    @DisplayName("Redis 연결이 끊긴 동안에는 명령을 쌓아 두지 않고 바로 거절한다(저장된 답이 없는 것으로 보고 AI를 부른다)")
    void rejectCommandsWhileDisconnected() {
        ClientOptions.Builder builder = ClientOptions.builder();

        new RecommendRedisClientConfig().recommendRedisClientOptionsCustomizer().customize(builder);

        assertThat(builder.build().getDisconnectedBehavior()).isEqualTo(DisconnectedBehavior.REJECT_COMMANDS);
    }
}
