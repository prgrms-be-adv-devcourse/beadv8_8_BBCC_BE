package com.bbcc.kidly.gateway.ratelimit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
class RateLimitSwitch {

    static final String KEY = "kidly:gateway:rate-limit:enabled";
    private final ReactiveStringRedisTemplate redis;
    private final GlobalRateLimitProperties properties;
    private volatile boolean enabled = true;
    private Disposable refresh;

    RateLimitSwitch(ReactiveStringRedisTemplate redis, GlobalRateLimitProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @PostConstruct
    void start() {
        refresh = Flux.interval(properties.switchRefresh())
            .concatMap(tick -> redis.opsForValue().get(KEY).defaultIfEmpty("true")
                .doOnNext(value -> enabled = Boolean.parseBoolean(value))
                .onErrorResume(e -> {
                    log.warn("요청 수 제한 설정을 읽지 못했습니다. exception={}", e.getClass().getName());
                    return Mono.empty();
                }))
            .subscribe();
    }

    @PreDestroy
    void stop() {
        if (refresh != null) {
            refresh.dispose();
        }
    }

    boolean isEnabled() {
        return enabled;
    }
}
