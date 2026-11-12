package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bbcc.kidly.boundedcontext.recommend.app.ai.AiResponseService;
import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.AiCallQuota;
import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.AiResponseStore;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseKey;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI 답을 기다리지 않고 바로 돌려주는 호출(respondAsync)을 확인한다. 화면에 규칙 결과를 먼저 들고 있다가
 * AI 답이 오면 바꾸는 SSE 응답이 쓴다.
 */
@ExtendWith(MockitoExtension.class)
class AiResponseServiceAsyncTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T15:30:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 11, 0, 30);
    private static final int DAILY_LIMIT = 2_000;
    private static final Map<AiResponseType, Duration> CALL_TIMEOUTS = Map.of(
        AiResponseType.HOME_PREVIEW, Duration.ofMillis(300),
        AiResponseType.BOUGHT_TOGETHER, Duration.ofMillis(300),
        AiResponseType.SIZE_RECOMMENDATION, Duration.ofMillis(300));
    private static final Duration ASYNC_WAIT = Duration.ofMillis(1_000);
    private static final Duration REUSE_PERIOD = Duration.ofHours(24);
    private static final AiResponseType TYPE = AiResponseType.BOUGHT_TOGETHER;
    private static final AiResponseKey.Input INPUT = new AiResponseKey.Input(
        7001L, null, null, null, null, null, null, List.of(3L, 1L, 2L), null, 3);

    @Mock
    private AiResponseStore store;

    @Mock
    private AiCallQuota quota;

    private AiResponseService service;

    @BeforeEach
    void setUp() {
        service = new AiResponseService(store, quota, JsonMapper.builder().build(), CLOCK, DAILY_LIMIT, CALL_TIMEOUTS,
            ASYNC_WAIT, REUSE_PERIOD);
    }

    @Test
    @DisplayName("저장한 답이 있으면 이미 끝난 결과로 돌려주고, AI를 부르지 않고 횟수도 세지 않는다")
    void returnCompletedResultWhenStored() {
        given(store.find(any(), any())).willReturn(Optional.of("{\"reason\":\"저장한 답\"}"));
        AtomicInteger calls = new AtomicInteger();

        CompletableFuture<Optional<Answer>> result = service.respondAsync(TYPE, INPUT, Answer.class, () -> {
            calls.incrementAndGet();
            return new Answer("새 답");
        });

        assertThat(result).isCompletedWithValue(Optional.of(new Answer("저장한 답")));
        assertThat(calls).hasValue(0);
        verifyNoInteractions(quota);
    }

    @Test
    @DisplayName("저장한 답이 없으면 AI를 기다리지 않고 바로 돌려주고, AI 답이 오면 저장한 뒤 그 답으로 끝난다")
    void returnBeforeAiAnswersAndCompleteWithAnswer() {
        given(store.find(any(), any())).willReturn(Optional.empty());
        given(quota.tryAcquire(any(), anyInt())).willReturn(true);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<Optional<Answer>> result = service.respondAsync(TYPE, INPUT, Answer.class, () -> {
            await(release);
            return new Answer("새 답");
        });

        assertThat(result).isNotDone();
        release.countDown();
        assertThat(result.join()).contains(new Answer("새 답"));
        verify(store, timeout(1_000)).save(TYPE, AiResponseKey.of(INPUT), "{\"reason\":\"새 답\"}", REUSE_PERIOD);
    }

    @Test
    @DisplayName("하루 상한에 닿았으면 AI를 부르지 않고 빈 값으로 끝난 결과를 돌려준다")
    void returnEmptyWhenDailyLimitReached() {
        given(store.find(any(), any())).willReturn(Optional.empty());
        given(quota.tryAcquire(any(), anyInt())).willReturn(false);
        AtomicInteger calls = new AtomicInteger();

        CompletableFuture<Optional<Answer>> result = service.respondAsync(TYPE, INPUT, Answer.class, () -> {
            calls.incrementAndGet();
            return new Answer("새 답");
        });

        assertThat(result).isCompletedWithValue(Optional.empty());
        assertThat(calls).hasValue(0);
    }

    @Test
    @DisplayName("AI 호출이 실패하거나 null을 돌려주면 빈 값으로 끝나고 저장하지 않는다")
    void completeEmptyWhenAiFailsOrReturnsNull() {
        given(store.find(any(), any())).willReturn(Optional.empty());
        given(quota.tryAcquire(any(), anyInt())).willReturn(true);

        CompletableFuture<Optional<Answer>> failed = service.respondAsync(TYPE, INPUT, Answer.class, () -> {
            throw new IllegalStateException("응답 본문");
        });
        CompletableFuture<Optional<Answer>> nullAnswer = service.respondAsync(TYPE, INPUT, Answer.class, () -> null);

        assertThat(failed.join()).isEmpty();
        assertThat(nullAnswer.join()).isEmpty();
        verify(store, never()).save(any(), any(), any(), any());
    }

    @Test
    @DisplayName("최대 시간 안에 AI 답이 오지 않으면 빈 값으로 끝나고, 그 뒤에 온 답은 저장해 다음 요청이 쓴다")
    void completeEmptyAfterAsyncWaitAndSaveLateAnswer() {
        given(store.find(any(), any())).willReturn(Optional.empty());
        given(quota.tryAcquire(any(), anyInt())).willReturn(true);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<Optional<Answer>> result = service.respondAsync(TYPE, INPUT, Answer.class, () -> {
            await(release);
            return new Answer("늦은 답");
        });

        assertThat(result.join()).isEmpty();
        release.countDown();
        verify(store, timeout(1_000)).save(TYPE, AiResponseKey.of(INPUT), "{\"reason\":\"늦은 답\"}", REUSE_PERIOD);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    record Answer(String reason) {
    }
}
