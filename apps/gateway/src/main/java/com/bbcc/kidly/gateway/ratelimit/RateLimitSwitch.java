package com.bbcc.kidly.gateway.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 전체 요청 수 제한 스위치(#1417). Gateway가 여러 대라 값은 Redis에 두고, 요청마다 Redis를 읽지 않도록 switchRefresh마다 읽어 메모리에 둔다.
 * 키는 TTL이 없어 volatile-lru에서 지워지지 않는다. 키가 없거나 "false"가 아니면 켜진다. 잘못 쓴 값으로 보호가 풀리지 않게 하려는 것이다.
 * Redis를 읽지 못하면 마지막 값을 유지한다.
 * 읽기는 SmartLifecycle로 Redis 연결(phase 0)보다 늦게 시작하고 먼저 멈춘다. 연결이 멈춘 동안(종료 · 테스트 컨텍스트 일시정지) 읽다 실패하지 않게 하려는 것이다.
 */
@Slf4j
@Component
class RateLimitSwitch implements SmartLifecycle {

    static final String KEY = "kidly:gateway:rate-limit:enabled";
    private final ReactiveStringRedisTemplate redis;
    private final GlobalRateLimitProperties properties;
    private volatile boolean enabled = true;
    private long version;
    private Disposable refresh;

    RateLimitSwitch(ReactiveStringRedisTemplate redis, GlobalRateLimitProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public void start() {
        refresh = Flux.interval(properties.switchRefresh())
            .concatMap(tick -> read())
            .subscribe();
    }

    private Mono<String> read() {
        long previous;
        synchronized (this) {
            previous = version;
        }
        return redis.opsForValue().get(KEY).defaultIfEmpty("true")
                .doOnNext(value -> updateIfUnchanged(value, previous))
                .onErrorResume(e -> {
                    log.warn("요청 수 제한 설정을 읽지 못했습니다. exception={}", e.getClass().getName());
                    return Mono.empty();
                });
    }

    // set 전에 보낸 읽기가 set 뒤에 도착해 방금 바꾼 값을 옛 값으로 되돌리지 않게 한다
    private synchronized void updateIfUnchanged(String value, long previous) {
        if (previous == version) {
            enabled = !"false".equals(value);
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

    boolean isEnabled() {
        return enabled;
    }

    Mono<Boolean> set(boolean value) {
        return redis.opsForValue().set(KEY, Boolean.toString(value)).map(ignored -> {
            synchronized (this) {
                version++;
                enabled = value;
            }
            return value;
        });
    }
}
