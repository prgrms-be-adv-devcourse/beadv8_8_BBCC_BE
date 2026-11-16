package com.bbcc.kidly.gateway.ratelimit;

import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Redis 토큰 버킷으로 요청 수를 세어 Gateway가 여러 대여도 한도를 공유한다(#1417).
 * 여러 버킷을 Lua 하나로 확인해 어느 한도가 부족해도 다른 한도의 토큰을 쓰지 않는다. 시각은 Gateway 시계 대신 Redis TIME을 써서
 * Gateway 사이 시계 오차가 한도를 바꾸지 않게 한다. 해시 TTL은 다 차는 시간이라 지워져도 새 버킷과 결과가 같다.
 * Redis에 닿지 못하면 통과시킨다(무효화 목록과 같은 정책). 로그에 키(IP · 회원 ID)를 남기지 않는다.
 */
@Slf4j
@Component
class SharedRequestRateLimiter {

    private static final String PREFIX = "kidly:gateway:rate-limit:";
    private static final RedisScript<String> ACQUIRE = RedisScript.of("""
        local time = redis.call('TIME')
        local now = time[1] * 1000 + math.floor(time[2] / 1000)
        local balances = {}
        for i, key in ipairs(KEYS) do
          local capacity = tonumber(ARGV[i * 2 - 1])
          local period = tonumber(ARGV[i * 2])
          local values = redis.call('HMGET', key, 'tokens', 'updated')
          local tokens = tonumber(values[1]) or capacity
          local updated = tonumber(values[2]) or now
          tokens = math.min(capacity, tokens + math.max(0, now - updated) * capacity / period)
          if tokens < 1 then
            local retry = math.max(1, math.ceil((1 - tokens) * period / capacity / 1000))
            return i .. ':' .. retry
          end
          balances[i] = tokens - 1
        end
        for i, key in ipairs(KEYS) do
          redis.call('HSET', key, 'tokens', tostring(balances[i]), 'updated', tostring(now))
          redis.call('PEXPIRE', key, tonumber(ARGV[i * 2]))
        end
        return '0:0'
        """, String.class);

    private final ReactiveStringRedisTemplate redis;

    SharedRequestRateLimiter(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    sealed interface Result {
        record Allowed() implements Result { }
        record Rejected(Bucket bucket, long retryAfterSeconds) implements Result { }
    }

    record Bucket(String name, String key, RateLimitProperties.Limit limit) {
        String redisKey() {
            return PREFIX + name + (key == null ? "" : ":" + key);
        }
    }

    Mono<Result> tryAcquire(String name, String key, RateLimitProperties.Limit limit) {
        return tryAcquire(List.of(new Bucket(name, key, limit)));
    }

    Mono<Result> tryAcquire(List<Bucket> buckets) {
        var keys = buckets.stream().map(Bucket::redisKey).toList();
        var args = buckets.stream().flatMap(bucket -> Stream.of(
            Integer.toString(bucket.limit().capacity()), Long.toString(bucket.limit().period().toMillis()))).toList();
        return redis.execute(ACQUIRE, keys, args)
            .single()
            .<Result>map(value -> {
                var parts = value.split(":");
                int index = Integer.parseInt(parts[0]);
                return index == 0 ? new Result.Allowed()
                    : new Result.Rejected(buckets.get(index - 1), Long.parseLong(parts[1]));
            })
            .onErrorResume(DataAccessException.class, e -> {
                log.error("요청 수를 확인하지 못해 통과시킵니다. exception={}", e.getClass().getName());
                return Mono.just(new Result.Allowed());
            });
    }
}
