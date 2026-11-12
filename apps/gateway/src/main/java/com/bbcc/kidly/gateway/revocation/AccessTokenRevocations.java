package com.bbcc.kidly.gateway.revocation;

import com.bbcc.kidly.shared.auth.PrincipalId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Mono;

/**
 * 무효화 해시와 파티션 표식은 TTL을 두지 않아 volatile-lru의 삭제 대상에서 뺀다.
 */
@Slf4j
public class AccessTokenRevocations {

    static final String KEY = "kidly:gateway:revocations";
    private static final String LOADED_KEY_PREFIX = KEY + ":loaded:";

    // 순서가 바뀌어 오거나 여러 대가 같은 메시지를 다시 써도 결과가 같게 더 늦은 시각만 남긴다. 무효화는 드물어 해시가 작으므로 매번 훑어 지운다
    private static final RedisScript<Long> REVOKE = RedisScript.of("""
        local current = redis.call('HGET', KEYS[1], ARGV[1])
        if not current or tonumber(current) < tonumber(ARGV[2]) then
          redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
        end
        local fields = redis.call('HGETALL', KEYS[1])
        for i = 1, #fields, 2 do
          if tonumber(fields[i + 1]) <= tonumber(ARGV[3]) then
            redis.call('HDEL', KEYS[1], fields[i])
          end
        end
        return 0
        """, Long.class);

    private final ReactiveStringRedisTemplate redis;
    private final Duration retention;

    public AccessTokenRevocations(ReactiveStringRedisTemplate redis, Duration retention) {
        this.redis = redis;
        this.retention = retention;
    }

    // 무효화 기록이 의미 있는 기간. 리스너가 되감을 기간이기도 하다
    public Duration retention() {
        return retention;
    }

    // 실패마다 WARN을 남긴다. spring-kafka 재시도 로그(INFO)에는 원인 예외가 빠진다
    public void revoke(PrincipalId principal, Instant revokedAt, Instant now) {
        var expiredUntil = now.minus(retention).getEpochSecond();
        redis.execute(REVOKE, List.of(KEY),
                List.of(principal.toSubject(), Long.toString(revokedAt.getEpochSecond()), Long.toString(expiredUntil)))
            .then()
            .doOnError(e -> log.warn("토큰 무효화를 Redis에 쓰지 못했습니다. exception={}", e.getClass().getName()))
            .block();
    }

    // Redis에 닿지 못하면 통과시킨다. 거절하면 Redis 장애가 로그인한 모든 요청의 장애가 된다. 놓친 토큰도 최대 30분 뒤 만료된다
    public Mono<Boolean> isRevoked(PrincipalId principal, Instant issuedAt, Instant now) {
        return redis.<String, String>opsForHash().get(KEY, principal.toSubject())
            .map(Long::parseLong)
            .map(revokedAt -> !isExpired(revokedAt, now) && issuedAt.getEpochSecond() <= revokedAt)
            .defaultIfEmpty(false)
            .onErrorResume(DataAccessException.class, e -> {
                log.error("토큰 무효화 목록을 읽지 못해 확인 없이 통과시킵니다. exception={}", e.getClass().getName());
                return Mono.just(false);
            });
    }

    public boolean isLoaded(int partition) {
        return redis.hasKey(LOADED_KEY_PREFIX + partition).block();
    }

    public void markLoaded(int partition) {
        redis.opsForValue().set(LOADED_KEY_PREFIX + partition, "1").block();
    }

    private boolean isExpired(long revokedAt, Instant now) {
        return !Instant.ofEpochSecond(revokedAt).plus(retention).isAfter(now);
    }
}
