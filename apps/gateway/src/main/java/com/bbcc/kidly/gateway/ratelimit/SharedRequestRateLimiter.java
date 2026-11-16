package com.bbcc.kidly.gateway.ratelimit;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Redis 토큰 버킷으로 요청 수를 세어 Gateway가 여러 대여도 한도를 공유한다(#1417).
 * 읽기 · 채우기 · 차감 · 쓰기를 Lua 하나로 해 여러 Gateway가 동시에 와도 끼어들 틈이 없다. 시각은 Gateway 시계 대신 Redis TIME을 써서
 * Gateway 사이 시계 오차가 한도를 바꾸지 않게 한다. 해시 TTL은 다 차는 시간이라 지워져도 새 버킷과 결과가 같다.
 * Redis에 닿지 못하면 통과시킨다(무효화 목록과 같은 정책). 로그에 키(IP · 회원 ID)를 남기지 않는다.
 */
@Slf4j
@Component
class SharedRequestRateLimiter {

    private static final String PREFIX = "kidly:gateway:rate-limit:";
    private static final RedisScript<Long> ACQUIRE = RedisScript.of("""
        local time = redis.call('TIME')
        local now = time[1] * 1000 + math.floor(time[2] / 1000)
        local capacity = tonumber(ARGV[1])
        local period = tonumber(ARGV[2])
        local values = redis.call('HMGET', KEYS[1], 'tokens', 'updated')
        local tokens = tonumber(values[1]) or capacity
        local updated = tonumber(values[2]) or now
        tokens = math.min(capacity, tokens + math.max(0, now - updated) * capacity / period)
        local retry = 0
        if tokens >= 1 then
          tokens = tokens - 1
        else
          retry = math.max(1, math.ceil((1 - tokens) * period / capacity / 1000))
        end
        redis.call('HSET', KEYS[1], 'tokens', tostring(tokens), 'updated', tostring(now))
        redis.call('PEXPIRE', KEYS[1], period)
        return retry
        """, Long.class);

    private final ReactiveStringRedisTemplate redis;
    SharedRequestRateLimiter(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    sealed interface Result {
        record Allowed() implements Result { }
        record Rejected(String limit, long retryAfterSeconds) implements Result { }
    }

    Mono<Result> tryAcquire(String name, String key, RateLimitProperties.Limit limit) {
        return redis.execute(ACQUIRE, List.of(PREFIX + name + ":" + key),
                List.of(Integer.toString(limit.capacity()), Long.toString(limit.period().toMillis())))
            .single()
            .<Result>map(retry -> retry == 0 ? new Result.Allowed() : new Result.Rejected(name, retry))
            .onErrorResume(DataAccessException.class, e -> {
                log.error("요청 수를 확인하지 못해 통과시킵니다. exception={}", e.getClass().getName());
                return Mono.just(new Result.Allowed());
            });
    }
}
