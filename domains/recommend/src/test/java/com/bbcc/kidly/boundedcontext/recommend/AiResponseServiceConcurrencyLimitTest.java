package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.recommend.app.ai.AiResponseService;
import com.bbcc.kidly.boundedcontext.recommend.app.ai.RecommendAiMetrics;
import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.AiCallQuota;
import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.AiResponseStore;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseKey;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

/**
 * 동시에 진행 중인 AI 호출 수의 상한을 확인한다. AI가 느려지면 답을 기다리는 호출이 그만큼 쌓여 힙을 채우므로,
 * 상한을 넘는 요청은 AI를 부르지 않고 빈 값(부른 쪽이 규칙 결과로 대신함)으로 돌려준다.
 * 자리는 요청이 끝난 때가 아니라 AI 호출이 실제로 끝난 때 돌아와야 한다. 화면이 먼저 끝나도 AI 호출은 이어지기 때문이다.
 */
@ExtendWith(MockitoExtension.class)
class AiResponseServiceConcurrencyLimitTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T15:30:00Z"), ZoneId.of("Asia/Seoul"));
    private static final int DAILY_LIMIT = 2_000;
    private static final int MAX_CONCURRENT_CALLS = 2;
    private static final Duration CALL_TIMEOUT = Duration.ofMillis(2_000);
    // 요청이 AI 호출보다 먼저 끝나는 경우를 만들 때 쓰는 종류의 제한 시간이다
    private static final Duration SHORT_CALL_TIMEOUT = Duration.ofMillis(100);
    private static final AiResponseType SHORT_TIMEOUT_TYPE = AiResponseType.SIZE_RECOMMENDATION;
    private static final Map<AiResponseType, Duration> CALL_TIMEOUTS = Map.of(
        AiResponseType.HOME_PREVIEW, CALL_TIMEOUT,
        AiResponseType.BOUGHT_TOGETHER, CALL_TIMEOUT,
        AiResponseType.SIZE_RECOMMENDATION, SHORT_CALL_TIMEOUT);
    private static final Duration ASYNC_WAIT = Duration.ofMillis(2_000);
    private static final Duration REUSE_PERIOD = Duration.ofHours(24);
    private static final AiResponseType TYPE = AiResponseType.HOME_PREVIEW;
    private static final AiResponseKey.Input INPUT = new AiResponseKey.Input(
        null, 5L, 36, null, null, null, null, List.of(3L, 1L, 2L), List.of(), 1);

    @Mock
    private AiResponseStore store;

    @Mock
    private AiCallQuota quota;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    // 진행 중인 AI 호출을 붙잡아 두었다가 테스트가 끝나면 모두 풀어 준다
    private final CountDownLatch release = new CountDownLatch(1);

    private AiResponseService service;

    @BeforeEach
    void setUp() {
        service = serviceWaitingAsyncAnswerFor(ASYNC_WAIT);
    }

    private AiResponseService serviceWaitingAsyncAnswerFor(Duration asyncWait) {
        return new AiResponseService(store, quota, JsonMapper.builder().build(), CLOCK, DAILY_LIMIT,
            MAX_CONCURRENT_CALLS, CALL_TIMEOUTS, asyncWait, REUSE_PERIOD,
            new RecommendAiMetrics(meterRegistry, ObservationRegistry.NOOP));
    }

    @AfterEach
    void releaseHeldCalls() {
        release.countDown();
    }

    @Test
    @DisplayName("상한만큼 AI 호출이 진행 중이면 다음 요청은 AI를 부르지 않고 AI 결과 없음을 돌려주며, 하루 횟수도 세지 않는다")
    void returnEmptyWithoutCallingAiWhenLimitReached() {
        given(store.find(any(), any())).willReturn(Optional.empty());
        given(quota.tryAcquire(any(), anyInt())).willReturn(true);
        holdCallsInBackground(MAX_CONCURRENT_CALLS);
        AtomicInteger calls = new AtomicInteger();

        Optional<Answer> result = service.respond(TYPE, INPUT, Answer.class, countingCall(calls));

        assertThat(result).isEmpty();
        assertThat(calls).hasValue(0);
        verify(quota, times(MAX_CONCURRENT_CALLS)).tryAcquire(any(), anyInt());
    }

    @Test
    @DisplayName("진행 중이던 AI 호출이 답하면 자리가 풀려 다음 요청이 AI를 부른다")
    void callAiAgainAfterHeldCallsAnswer() {
        given(store.find(any(), any())).willReturn(Optional.empty());
        given(quota.tryAcquire(any(), anyInt())).willReturn(true);
        List<CompletableFuture<Optional<Answer>>> held = holdCallsInBackground(MAX_CONCURRENT_CALLS);
        release.countDown();
        held.forEach(CompletableFuture::join);
        AtomicInteger calls = new AtomicInteger();

        Optional<Answer> result = service.respond(TYPE, INPUT, Answer.class, countingCall(calls));

        assertThat(result).contains(new Answer("새 답"));
        assertThat(calls).hasValue(1);
    }

    enum Failure { THROWS, RETURNS_NULL }

    @ParameterizedTest
    @EnumSource(Failure.class)
    @DisplayName("AI 호출이 예외를 던지거나 null을 돌려줘도 자리가 풀린다")
    void releaseSlotWhenAiFailsOrReturnsNull(Failure failure) {
        given(store.find(any(), any())).willReturn(Optional.empty());
        given(quota.tryAcquire(any(), anyInt())).willReturn(true);
        for (int i = 0; i < MAX_CONCURRENT_CALLS; i++) {
            assertThat(service.respond(TYPE, INPUT, Answer.class, failingCall(failure))).isEmpty();
        }
        AtomicInteger calls = new AtomicInteger();

        Optional<Answer> result = service.respond(TYPE, INPUT, Answer.class, countingCall(calls));

        assertThat(result).contains(new Answer("새 답"));
        assertThat(calls).hasValue(1);
    }

    private static Supplier<Answer> failingCall(Failure failure) {
        return () -> {
            if (failure == Failure.THROWS) {
                throw new IllegalStateException("응답 본문");
            }
            return null;
        };
    }

    @Test
    @DisplayName("AI 답이 제한 시간을 넘겨 요청이 먼저 끝나도 그 AI 호출이 끝날 때까지 자리를 쥐고 있고, 끝나면 풀린다")
    void keepSlotUntilAiCallEndsEvenAfterRequestTimedOut() {
        given(store.find(any(), any())).willReturn(Optional.empty());
        given(quota.tryAcquire(any(), anyInt())).willReturn(true);
        CountDownLatch started = new CountDownLatch(MAX_CONCURRENT_CALLS);
        for (int i = 0; i < MAX_CONCURRENT_CALLS; i++) {
            assertThat(service.respond(SHORT_TIMEOUT_TYPE, INPUT, Answer.class, heldCall(started))).isEmpty();
        }
        AtomicInteger calls = new AtomicInteger();

        Optional<Answer> whileHeld = service.respond(TYPE, INPUT, Answer.class, countingCall(calls));

        assertThat(whileHeld).isEmpty();
        assertThat(calls).hasValue(0);
        release.countDown();
        assertThat(respondOnceSlotIsFree(calls)).contains(new Answer("새 답"));
    }

    // 자리는 AI 호출을 돌리던 스레드가 돌려놓으므로, 돌아올 때까지 잠깐씩 쉬며 다시 묻는다
    private Optional<Answer> respondOnceSlotIsFree(AtomicInteger calls) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Optional<Answer> result = service.respond(TYPE, INPUT, Answer.class, countingCall(calls));
            if (calls.get() > 0) {
                return result;
            }
            sleep(Duration.ofMillis(10));
        }
        return Optional.empty();
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    enum QuotaRefusal { DAILY_LIMIT, QUOTA_THROWS }

    @ParameterizedTest
    @EnumSource(QuotaRefusal.class)
    @DisplayName("하루 상한에 닿았거나 횟수를 세다 오류가 나서 AI를 부르지 않은 요청은 자리를 쓰지 않는다")
    void doNotHoldSlotWhenQuotaRefuses(QuotaRefusal refusal) {
        given(store.find(any(), any())).willReturn(Optional.empty());
        if (refusal == QuotaRefusal.DAILY_LIMIT) {
            given(quota.tryAcquire(any(), anyInt())).willReturn(false, false, true);
        } else {
            given(quota.tryAcquire(any(), anyInt()))
                .willThrow(new IllegalStateException("횟수 표 오류"))
                .willThrow(new IllegalStateException("횟수 표 오류"))
                .willReturn(true);
        }
        AtomicInteger calls = new AtomicInteger();
        for (int i = 0; i < MAX_CONCURRENT_CALLS; i++) {
            assertThat(service.respond(TYPE, INPUT, Answer.class, countingCall(calls))).isEmpty();
        }

        Optional<Answer> result = service.respond(TYPE, INPUT, Answer.class, countingCall(calls));

        assertThat(result).contains(new Answer("새 답"));
        assertThat(calls).hasValue(1);
    }

    @Test
    @DisplayName("저장한 답이 있으면 상한에 닿아 있어도 그 답을 돌려준다")
    void returnStoredAnswerEvenWhenLimitReached() {
        given(store.find(any(), any()))
            .willReturn(Optional.empty(), Optional.empty(), Optional.of("{\"reason\":\"저장한 답\"}"));
        given(quota.tryAcquire(any(), anyInt())).willReturn(true);
        holdCallsInBackground(MAX_CONCURRENT_CALLS);
        AtomicInteger calls = new AtomicInteger();

        Optional<Answer> result = service.respond(TYPE, INPUT, Answer.class, countingCall(calls));

        assertThat(result).contains(new Answer("저장한 답"));
        assertThat(calls).hasValue(0);
    }

    @Test
    @DisplayName("상한에 걸리면 요청 결과 busy를 하나 세고 error · daily_limit은 세지 않는다")
    void countBusyWhenLimitReached() {
        given(store.find(any(), any())).willReturn(Optional.empty());
        given(quota.tryAcquire(any(), anyInt())).willReturn(true);
        holdCallsInBackground(MAX_CONCURRENT_CALLS);

        service.respond(TYPE, INPUT, Answer.class, () -> new Answer("새 답"));

        assertThat(requestCount("busy")).isEqualTo(1);
        assertThat(requestCount("error")).isZero();
        assertThat(requestCount("daily_limit")).isZero();
    }

    private double requestCount(String result) {
        return meterRegistry.get("kidly.recommend.ai.requests")
            .tag("feature", "home_preview").tag("result", result).counter().count();
    }

    @Test
    @DisplayName("기다리지 않는 호출도 상한만큼 진행 중이면 AI를 부르지 않고 빈 값으로 이미 끝난 결과를 돌려주며, busy를 하나 센다")
    void returnCompletedEmptyWithoutCallingAiWhenLimitReachedAsync() {
        given(store.find(any(), any())).willReturn(Optional.empty());
        given(quota.tryAcquire(any(), anyInt())).willReturn(true);
        holdCallsInBackground(MAX_CONCURRENT_CALLS);
        AtomicInteger calls = new AtomicInteger();

        CompletableFuture<Optional<Answer>> result =
            service.respondAsync(TYPE, INPUT, Answer.class, countingCall(calls));

        assertThat(result).isCompletedWithValue(Optional.empty());
        assertThat(calls).hasValue(0);
        assertThat(requestCount("busy")).isEqualTo(1);
    }

    @Test
    @DisplayName("기다리는 호출과 기다리지 않는 호출이 같은 상한을 함께 쓴다")
    void shareOneLimitBetweenWaitingAndAsyncCalls() {
        given(store.find(any(), any())).willReturn(Optional.empty());
        given(quota.tryAcquire(any(), anyInt())).willReturn(true);
        holdCallsInBackground(MAX_CONCURRENT_CALLS - 1);
        CountDownLatch started = new CountDownLatch(1);
        service.respondAsync(TYPE, INPUT, Answer.class, heldCall(started));
        awaitAll(started);
        AtomicInteger calls = new AtomicInteger();

        Optional<Answer> result = service.respond(TYPE, INPUT, Answer.class, countingCall(calls));

        assertThat(result).isEmpty();
        assertThat(calls).hasValue(0);
    }

    enum AsyncEnd { ANSWERS, THROWS }

    @ParameterizedTest
    @EnumSource(AsyncEnd.class)
    @DisplayName("기다리지 않는 호출도 AI 답이 오거나 실패하면 자리가 풀려 다음 요청이 AI를 부른다")
    void releaseSlotWhenAsyncCallEnds(AsyncEnd end) {
        given(store.find(any(), any())).willReturn(Optional.empty());
        given(quota.tryAcquire(any(), anyInt())).willReturn(true);
        for (int i = 0; i < MAX_CONCURRENT_CALLS; i++) {
            service.respondAsync(TYPE, INPUT, Answer.class, () -> {
                if (end == AsyncEnd.THROWS) {
                    throw new IllegalStateException("응답 본문");
                }
                return new Answer("먼저 온 답");
            }).join();
        }
        AtomicInteger calls = new AtomicInteger();

        CompletableFuture<Optional<Answer>> result =
            service.respondAsync(TYPE, INPUT, Answer.class, countingCall(calls));

        assertThat(result.join()).contains(new Answer("새 답"));
        assertThat(calls).hasValue(1);
    }

    @Test
    @DisplayName("최대 대기 시간을 넘겨 빈 값으로 끝난 뒤에도 그 AI 호출이 끝날 때까지 자리를 쥐고 있고, 끝나면 풀린다")
    void keepSlotUntilAiCallEndsEvenAfterAsyncWaitPassed() {
        given(store.find(any(), any())).willReturn(Optional.empty());
        given(quota.tryAcquire(any(), anyInt())).willReturn(true);
        service = serviceWaitingAsyncAnswerFor(Duration.ofMillis(100));
        CountDownLatch started = new CountDownLatch(MAX_CONCURRENT_CALLS);
        for (int i = 0; i < MAX_CONCURRENT_CALLS; i++) {
            assertThat(service.respondAsync(TYPE, INPUT, Answer.class, heldCall(started)).join()).isEmpty();
        }
        AtomicInteger calls = new AtomicInteger();

        CompletableFuture<Optional<Answer>> whileHeld =
            service.respondAsync(TYPE, INPUT, Answer.class, countingCall(calls));

        assertThat(whileHeld).isCompletedWithValue(Optional.empty());
        assertThat(calls).hasValue(0);
        release.countDown();
        assertThat(respondOnceSlotIsFree(calls)).contains(new Answer("새 답"));
    }

    // 다른 스레드에서 respond를 count번 부르고, 그 AI 호출이 모두 시작돼 답을 기다리는 상태가 될 때까지 기다린다
    private List<CompletableFuture<Optional<Answer>>> holdCallsInBackground(int count) {
        CountDownLatch started = new CountDownLatch(count);
        List<CompletableFuture<Optional<Answer>>> held = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            held.add(CompletableFuture.supplyAsync(
                () -> service.respond(TYPE, INPUT, Answer.class, heldCall(started))));
        }
        awaitAll(started);
        return held;
    }

    private Supplier<Answer> heldCall(CountDownLatch started) {
        return () -> {
            started.countDown();
            awaitAll(release);
            return new Answer("붙잡혀 있던 답");
        };
    }

    private static Supplier<Answer> countingCall(AtomicInteger calls) {
        return () -> {
            calls.incrementAndGet();
            return new Answer("새 답");
        };
    }

    private static void awaitAll(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).as("5초 안에 준비되지 않았다").isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    record Answer(String reason) {
    }
}
