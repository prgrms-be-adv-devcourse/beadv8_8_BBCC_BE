package com.bbcc.kidly.gateway.ratelimit;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 전체 요청 수 제한의 런타임 설정(#1417 스위치, #1421 한도). 부하테스트 중 재배포 없이 끄거나 한도를 바꾸려는 것이다.
 * Gateway가 여러 대라 값은 Redis 해시에 두고, 요청마다 Redis를 읽지 않도록 settingsRefresh마다 읽어 메모리 스냅샷으로 둔다.
 * 해시는 TTL이 없어 volatile-lru에서 지워지지 않는다. enabled는 정확히 "false"일 때만 꺼지고, 한도는 두 필드가 모두 맞는 값일 때만 쓰고
 * 아니면 yml 기본값이다. 잘못 쓴 값으로 보호가 풀리거나 0으로 나누지 않게 하려는 것이다. Redis를 읽지 못하면 마지막 값을 유지한다.
 * 읽기는 SmartLifecycle로 Redis 연결(phase 0)보다 늦게 시작하고 먼저 멈춘다. 연결이 멈춘 동안(종료 · 테스트 컨텍스트 일시정지) 읽다 실패하지 않게 하려는 것이다.
 */
@Slf4j
@Component
class RateLimitSettings implements SmartLifecycle {

    static final String KEY = "kidly:gateway:rate-limit:settings";
    private final ReactiveStringRedisTemplate redis;
    private final RateLimitProperties properties;
    private volatile Snapshot snapshot;
    private long version;
    private Disposable refresh;

    record Snapshot(boolean enabled, RateLimitProperties.Limit client, RateLimitProperties.Limit total) {
    }

    /** null인 칸은 바꾸지 않는다. */
    record Change(Boolean enabled, RateLimitProperties.Limit client, RateLimitProperties.Limit total) {
    }

    RateLimitSettings(ReactiveStringRedisTemplate redis, RateLimitProperties properties) {
        this.redis = redis;
        this.properties = properties;
        this.snapshot = new Snapshot(true, properties.client(), properties.total());
    }

    @Override
    public void start() {
        // 읽기가 주기보다 오래 걸리면 interval 이 OverflowException 으로 끝나 다시 읽지 않는다. 그동안 온 틱은 버린다
        refresh = Flux.interval(properties.settingsRefresh())
            .onBackpressureDrop()
            .concatMap(tick -> read())
            .subscribe();
    }

    private Mono<Void> read() {
        long previous;
        synchronized (this) {
            previous = version;
        }
        return redis.<String, String>opsForHash().entries(KEY)
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .doOnNext(values -> updateIfUnchanged(values, previous))
                .onErrorResume(e -> {
                    log.warn("요청 수 제한 설정을 읽지 못했습니다. exception={}", e.getClass().getName());
                    return Mono.empty();
                })
                .then();
    }

    // set 전에 보낸 읽기가 set 뒤에 도착해 방금 바꾼 값을 옛 값으로 되돌리지 않게 한다
    private synchronized void updateIfUnchanged(Map<String, String> values, long previous) {
        if (previous == version) {
            snapshot = new Snapshot(!"false".equals(values.get("enabled")),
                limit(values, "client", properties.client()), limit(values, "total", properties.total()));
        }
    }

    private static RateLimitProperties.Limit limit(Map<String, String> values, String name,
        RateLimitProperties.Limit fallback) {
        try {
            int capacity = Integer.parseInt(values.get(name + "-capacity"));
            long period = Long.parseLong(values.get(name + "-period-ms"));
            return capacity >= 1 && period >= 1
                ? new RateLimitProperties.Limit(capacity, Duration.ofMillis(period)) : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public void stop() {
        if (refresh != null) {
            refresh.dispose();
        }
    }

    @Override
    public boolean isRunning() {
        return refresh != null && !refresh.isDisposed();
    }

    Snapshot snapshot() {
        return snapshot;
    }

    // 바뀐 필드만 HSET 한 번으로 쓴다. 읽고 고쳐 쓰지 않아 관리자 둘이 다른 한도를 동시에 바꿔도 서로 덮지 않는다
    Mono<Snapshot> set(Change change) {
        var fields = new HashMap<String, String>();
        if (change.enabled() != null) {
            fields.put("enabled", change.enabled().toString());
        }
        putLimit(fields, "client", change.client());
        putLimit(fields, "total", change.total());
        return redis.<String, String>opsForHash().putAll(KEY, fields).map(ignored -> {
            synchronized (this) {
                version++;
                snapshot = new Snapshot(change.enabled() == null ? snapshot.enabled() : change.enabled(),
                    change.client() == null ? snapshot.client() : change.client(),
                    change.total() == null ? snapshot.total() : change.total());
                return snapshot;
            }
        });
    }

    private static void putLimit(Map<String, String> fields, String name, RateLimitProperties.Limit limit) {
        if (limit != null) {
            fields.put(name + "-capacity", Integer.toString(limit.capacity()));
            fields.put(name + "-period-ms", Long.toString(limit.period().toMillis()));
        }
    }
}
