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
 * 주체별 Access Token 무효화 시각(Redis, #1327). Gateway가 여러 대여도, 다시 떠도 같은 목록을 본다.
 * 해시 하나(KEY)에 필드 = 토큰 주체(PrincipalId.toSubject), 값 = 무효화 시각(epoch 초)을 둔다. 키에 TTL을 두지 않는다
 * (Redis를 추천 캐시와 같이 쓰고, volatile-lru는 TTL 있는 키만 지운다).
 * 토큰 iat의 초가 무효화 시각의 초와 같거나 이전이면 거절하고, 보존 기간(토큰 유효 기간 + 시계 오차)이 지나면 무시한다.
 * now를 받는 것은 테스트가 시각을 정하기 위해서다. 부르는 쪽은 Instant.now()를 넘긴다.
 */
@Slf4j
public class AccessTokenRevocations {

    private static final String KEY = "kidly:gateway:revocations";

    // 더 늦은 시각만 남기고(순서가 바뀌어 오거나 여러 대가 같은 메시지를 다시 써도 결과가 같다), 보존 기간이 지난 필드를 지운다.
    // 무효화는 드물어 해시가 작으므로 매번 훑는다
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

    // Kafka 리스너(consumer 스레드)가 부르므로 기다린다. 실패하면 DataAccessException을 던져 리스너가 다시 시도한다.
    // 재시도 실패는 spring-kafka가 DEBUG로만 남겨, 여기서 남기지 않으면 리스너가 멈춘 것을 알 수 없다
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
                // 토큰 · 주체는 남기지 않는다
                log.error("토큰 무효화 목록을 읽지 못해 확인 없이 통과시킵니다. exception={}", e.getClass().getName());
                return Mono.just(false);
            });
    }

    private boolean isExpired(long revokedAt, Instant now) {
        return !Instant.ofEpochSecond(revokedAt).plus(retention).isAfter(now);
    }
}
