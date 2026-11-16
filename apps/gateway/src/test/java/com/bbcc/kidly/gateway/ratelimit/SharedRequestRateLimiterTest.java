package com.bbcc.kidly.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Flux;

@ExtendWith(OutputCaptureExtension.class)
class SharedRequestRateLimiterTest {

    @Test
    @DisplayName("Redis 장애에는 통과시키고 로그에 키를 남기지 않는다")
    void passesWhenRedisUnavailable(CapturedOutput output) {
        var redis = mock(ReactiveStringRedisTemplate.class);
        when(redis.execute(any(), anyList(), anyList()))
            .thenReturn(Flux.error(new DataAccessResourceFailureException("실패")));
        var properties = new GlobalRateLimitProperties(2, Duration.ofSeconds(2), Duration.ofSeconds(1));
        var limiter = new SharedRequestRateLimiter(redis, properties);

        assertThat(limiter.tryAcquire("private-id").block()).isZero();
        assertThat(output.getOut()).contains("ERROR").doesNotContain("private-id");
    }
}
