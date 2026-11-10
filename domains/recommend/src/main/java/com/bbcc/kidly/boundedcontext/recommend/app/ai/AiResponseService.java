package com.bbcc.kidly.boundedcontext.recommend.app.ai;

import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.AiCallQuota;
import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.AiResponseStore;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseKey;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseType;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI 호출의 공통 입구다. 같은 종류 · 같은 입력이면 재사용 기간 안에 저장한 응답을 다시 쓴다.
 * 이 서비스는 트랜잭션을 걸지 않는다. 호출 횟수 줄이 AI 응답을 기다리는 동안 잠기지 않게 하기 위해서다.
 */
@Slf4j
@Service
public class AiResponseService {

    private final AiResponseStore store;
    private final AiCallQuota quota;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final int dailyCallLimit;
    private final Duration callTimeout;
    private final Duration reusePeriod;

    @Autowired
    public AiResponseService(
        AiResponseStore store,
        AiCallQuota quota,
        JsonMapper jsonMapper,
        @Value("${kidly.recommend.ai.daily-call-limit:2000}") int dailyCallLimit,
        @Value("${kidly.recommend.ai.call-timeout:PT2.5S}") Duration callTimeout,
        @Value("${kidly.recommend.ai.response-reuse-period:PT24H}") Duration reusePeriod
    ) {
        this(store, quota, jsonMapper, Clock.system(ZoneId.of("Asia/Seoul")),
            dailyCallLimit, callTimeout, reusePeriod);
    }

    public AiResponseService(
        AiResponseStore store,
        AiCallQuota quota,
        JsonMapper jsonMapper,
        Clock clock,
        int dailyCallLimit,
        Duration callTimeout,
        Duration reusePeriod
    ) {
        this.store = store;
        this.quota = quota;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.dailyCallLimit = dailyCallLimit;
        this.callTimeout = callTimeout;
        this.reusePeriod = reusePeriod;
    }

    public <T> Optional<T> respond(
        AiResponseType type,
        AiResponseKey.Input input,
        Class<T> responseType,
        Supplier<T> call
    ) {
        LocalDateTime now = LocalDateTime.now(clock);
        AiResponseKey key = AiResponseKey.of(input);
        Optional<String> stored = store.findLatestSince(type, key, now.minus(reusePeriod));
        if (stored.isPresent()) {
            return Optional.of(jsonMapper.readValue(stored.get(), responseType));
        }
        if (!quota.tryAcquire(now.toLocalDate(), dailyCallLimit)) {
            return Optional.empty();
        }
        T result = callWithTimeout(call);
        store.save(type, key, jsonMapper.writeValueAsString(result), now);
        return Optional.of(result);
    }

    private <T> T callWithTimeout(Supplier<T> call) {
        FutureTask<T> task = new FutureTask<>(call::get);
        Thread.ofVirtual().start(task);
        try {
            return task.get(callTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new IllegalStateException("AI 호출에 실패했습니다", e);
        }
    }
}
