package com.bbcc.kidly.boundedcontext.recommend.app.ai;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.AiCallQuota;
import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.AiResponseStore;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseKey;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseType;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI 호출의 공통 입구다. 같은 종류 · 같은 입력이면 재사용 기간 안에 저장한 응답을 다시 쓴다.
 * 이 서비스는 트랜잭션을 걸지 않는다. 호출 횟수 줄이 AI 응답을 기다리는 동안 잠기지 않게 하기 위해서다.
 */
@Slf4j
@Service
public class AiResponseService {

    private static final Executor VIRTUAL_THREAD = Thread.ofVirtual()::start;

    private final AiResponseStore store;
    private final AiCallQuota quota;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final int dailyCallLimit;
    private final Map<AiResponseType, Duration> callTimeouts;
    private final Duration asyncWait;
    private final Duration reusePeriod;
    private final RecommendAiMetrics metrics;

    @Autowired
    public AiResponseService(
        AiResponseStore store,
        AiCallQuota quota,
        JsonMapper jsonMapper,
        @Value("${kidly.recommend.ai.daily-call-limit:2000}") int dailyCallLimit,
        // 화면이 아래쪽에 있어 늦게 보여도 되는 기능일수록 길게 기다린다. Gateway의 추천 경로 제한 시간보다 짧아야 한다
        @Value("${kidly.recommend.ai.call-timeout.home-preview:PT3S}") Duration homePreviewTimeout,
        @Value("${kidly.recommend.ai.call-timeout.bought-together:PT4S}") Duration boughtTogetherTimeout,
        @Value("${kidly.recommend.ai.call-timeout.size-recommendation:PT3.5S}") Duration sizeRecommendationTimeout,
        // 기다리지 않는 호출(SSE)이 AI 답을 받아 화면에 보낼 수 있는 최대 시간이다. 넘겨서 온 답도 저장해 다음 요청이 쓴다
        @Value("${kidly.recommend.ai.async-wait:PT10S}") Duration asyncWait,
        @Value("${kidly.recommend.ai.response-reuse-period:PT24H}") Duration reusePeriod,
        RecommendAiMetrics metrics
    ) {
        this(store, quota, jsonMapper, Clock.system(ZoneId.of("Asia/Seoul")), dailyCallLimit,
            Map.of(AiResponseType.HOME_PREVIEW, homePreviewTimeout,
                AiResponseType.BOUGHT_TOGETHER, boughtTogetherTimeout,
                AiResponseType.SIZE_RECOMMENDATION, sizeRecommendationTimeout),
            asyncWait, reusePeriod, metrics);
    }

    public AiResponseService(
        AiResponseStore store,
        AiCallQuota quota,
        JsonMapper jsonMapper,
        Clock clock,
        int dailyCallLimit,
        Map<AiResponseType, Duration> callTimeouts,
        Duration asyncWait,
        Duration reusePeriod,
        RecommendAiMetrics metrics
    ) {
        this.store = store;
        this.quota = quota;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.dailyCallLimit = dailyCallLimit;
        this.callTimeouts = Map.copyOf(callTimeouts);
        this.asyncWait = asyncWait;
        this.reusePeriod = reusePeriod;
        this.metrics = metrics;
    }

    /**
     * 같은 종류 · 같은 입력의 저장된 답이 있으면 그것을 돌려주고, 없으면 {@code call}로 AI를 불러 답을 돌려준다.
     * 받은 답은 다른 스레드에서 저장하므로, 돌려받은 직후에는 아직 저장되지 않았을 수 있다.
     * <ul>
     *   <li>빈 값은 AI 결과가 없다는 뜻이다. 부른 쪽이 규칙 결과로 대신한다.
     *       호출이 실패하거나 제한 시간을 넘기거나 null을 돌려줘도, 하루 상한에 닿아도 빈 값이다.</li>
     *   <li>트랜잭션 밖에서 불러야 한다. 안에서 부르면 {@link IllegalStateException}을 던진다.</li>
     *   <li>{@code call}은 다른 스레드에서 돈다. 트랜잭션, 지연 로딩, MDC가 따라가지 않으므로
     *       프롬프트에 쓸 입력은 부르기 전에 모두 읽어 둔다.</li>
     *   <li>응답 형식(record의 칸 이름 · 타입 · 의미)을 바꾸면 입력의 프롬프트 버전을 올린다.
     *       저장된 답은 모르는 칸을 무시하고 읽으므로, 올리지 않으면 옛 답이 새 칸 없이 재사용 기간 동안 쓰인다.</li>
     *   <li>응답 타입은 {@code Class}로 읽으므로 {@code List} 같은 제네릭은 record 하나로 감싼다.</li>
     *   <li>제한 시간은 종류마다 다르다. 넘기면 기다리지 않고 빈 값을 돌려주지만, 늦게 온 답은 받는 대로 저장해
     *       재사용 기간 동안 같은 입력의 다음 요청이 쓴다.</li>
     *   <li>실패하거나 제한 시간을 넘긴 호출도 하루 호출 횟수에 든다.</li>
     *   <li>저장된 답은 재사용 기간 안의 옛 답일 수 있으니, 상품이 지금 판매 중인지는 부른 쪽이 다시 거른다.</li>
     *   <li>입력의 ID 목록에 null이 있으면 키를 만들지 못해 예외를 던진다.</li>
     * </ul>
     */
    public <T> Optional<T> respond(
        AiResponseType type,
        AiResponseKey.Input input,
        Class<T> responseType,
        Supplier<T> call
    ) {
        // 트랜잭션 안에서 부르면 하루 횟수 줄의 잠금이 AI 응답을 기다리는 동안 이어져 모든 AI 호출이 줄을 선다.
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("AI 응답 서비스는 트랜잭션 밖에서 불러야 합니다.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        AiResponseKey key = AiResponseKey.of(input);
        Optional<T> stored = findStored(type, key, responseType);
        if (stored.isPresent()) {
            metrics.recordRequest(type, RecommendAiMetrics.RequestResult.STORED);
            return stored;
        }
        if (!tryAcquireQuietly(type, now)) {
            return Optional.empty();
        }
        Optional<T> result = callWithTimeout(type, key, call);
        if (result.isEmpty()) {
            return Optional.empty();
        }
        metrics.recordRequest(type, RecommendAiMetrics.RequestResult.ANSWERED);
        // 저장소가 느려도 화면 응답이 늦어지지 않게 저장은 기다리지 않는다
        T answer = result.get();
        CompletableFuture.runAsync(() -> saveQuietly(type, key, answer), VIRTUAL_THREAD);
        return result;
    }

    /**
     * AI 답을 기다리지 않고 바로 돌려준다. 돌려준 결과는 AI 답이 오면 그 답으로, 답을 얻지 못하면 빈 값으로 나중에 끝난다.
     * 저장한 답이 있으면 이미 끝난 결과다. 그 밖의 규칙(트랜잭션 밖에서 부르기, 하루 상한, 저장)은 {@link #respond}와 같다.
     */
    public <T> CompletableFuture<Optional<T>> respondAsync(
        AiResponseType type,
        AiResponseKey.Input input,
        Class<T> responseType,
        Supplier<T> call
    ) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("AI 응답 서비스는 트랜잭션 밖에서 불러야 합니다.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        AiResponseKey key = AiResponseKey.of(input);
        Optional<T> stored = findStored(type, key, responseType);
        if (stored.isPresent()) {
            metrics.recordRequest(type, RecommendAiMetrics.RequestResult.STORED);
            return CompletableFuture.completedFuture(stored);
        }
        if (!tryAcquireQuietly(type, now)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        CompletableFuture<T> task =
            CompletableFuture.supplyAsync(metrics.observeCall(type, call), VIRTUAL_THREAD);
        // 답이 오는 대로 저장한다. 최대 시간을 넘겨 화면에 보내지 못한 답도 저장해 다음 요청이 쓴다
        task.thenAccept(answer -> saveLateAnswer(type, key, answer));
        return task.handle((answer, error) -> toResult(type, answer, error))
            .completeOnTimeout(Optional.empty(), asyncWait.toMillis(), TimeUnit.MILLISECONDS);
    }

    // AI 호출이 실패하면 클래스 이름만 남기고 빈 값으로 끝낸다
    private <T> Optional<T> toResult(AiResponseType type, T answer, Throwable error) {
        if (error == null) {
            return Optional.ofNullable(answer);
        }
        Throwable cause = error;
        if (error instanceof CompletionException && error.getCause() != null) {
            cause = error.getCause();
        }
        logCallFailure(type, cause);
        return Optional.empty();
    }

    private boolean tryAcquireQuietly(AiResponseType type, LocalDateTime now) {
        try {
            return quota.tryAcquire(now.toLocalDate(), dailyCallLimit);
        } catch (RuntimeException e) {
            log.warn("AI 호출 횟수를 세지 못해 호출하지 않습니다. type={}, error={}",
                type, e.getClass().getSimpleName());
            return false;
        }
    }

    private <T> Optional<T> findStored(
        AiResponseType type,
        AiResponseKey key,
        Class<T> responseType
    ) {
        try {
            Optional<String> json = store.find(type, key);
            T value = null;
            if (json.isPresent()) {
                value = jsonMapper.readValue(json.get(), responseType);
            }
            if (value == null) {
                metrics.recordLookup(type, RecommendAiMetrics.LookupResult.MISS);
                return Optional.empty();
            }
            metrics.recordLookup(type, RecommendAiMetrics.LookupResult.HIT);
            return Optional.of(value);
        } catch (RuntimeException e) {
            log.warn("저장된 AI 응답을 읽지 못해 없는 것으로 봅니다. type={}, error={}",
                type, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private <T> void saveQuietly(AiResponseType type, AiResponseKey key, T response) {
        try {
            store.save(type, key, jsonMapper.writeValueAsString(response), reusePeriod);
        } catch (RuntimeException e) {
            log.warn("AI 응답을 저장하지 못했지만 받은 결과는 돌려줍니다. type={}, error={}",
                type, e.getClass().getSimpleName());
        }
    }

    private <T> Optional<T> callWithTimeout(AiResponseType type, AiResponseKey key, Supplier<T> call) {
        Duration callTimeout = requireNonNull(callTimeouts.get(type), "종류별 제한 시간이 없습니다");
        CompletableFuture<T> task =
            CompletableFuture.supplyAsync(metrics.observeCall(type, call), VIRTUAL_THREAD);
        try {
            return Optional.ofNullable(task.get(callTimeout.toMillis(), TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            task.cancel(true);
            logCallFailure(type, e);
        } catch (TimeoutException e) {
            // 화면에는 규칙 결과가 나가지만, 늦게 온 답은 받는 대로 저장해 같은 입력의 다음 요청이 쓰게 한다
            task.thenAccept(lateAnswer -> saveLateAnswer(type, key, lateAnswer));
            logCallFailure(type, e);
        } catch (ExecutionException e) {
            logCallFailure(type, e.getCause());
        }
        return Optional.empty();
    }

    private <T> void saveLateAnswer(AiResponseType type, AiResponseKey key, T lateAnswer) {
        if (lateAnswer != null) {
            saveQuietly(type, key, lateAnswer);
        }
    }

    // 예외 메시지에 응답 본문이나 개인정보가 섞일 수 있어 클래스 이름만 남기고 스택 트레이스는 붙이지 않는다.
    private void logCallFailure(AiResponseType type, Throwable cause) {
        log.warn("AI 호출에 실패해 결과 없이 돌려줍니다. type={}, error={}", type, cause.getClass().getSimpleName());
    }
}
