package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bbcc.kidly.boundedcontext.recommend.app.ai.RecommendAiMetrics;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import com.bbcc.kidly.boundedcontext.recommend.app.ai.AiResponseService;
import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.AiCallQuota;
import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.AiResponseStore;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseKey;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class AiResponseServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    // UTC 11-10 15:30은 한국 시간 11-11 00:30이다
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T15:30:00Z"), SEOUL);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 11, 0, 30);
    private static final int DAILY_LIMIT = 2_000;
    private static final Map<AiResponseType, Duration> CALL_TIMEOUTS = Map.of(
        AiResponseType.HOME_PREVIEW, Duration.ofMillis(300),
        AiResponseType.BOUGHT_TOGETHER, Duration.ofMillis(300),
        AiResponseType.SIZE_RECOMMENDATION, Duration.ofMillis(300));
    private static final Duration REUSE_PERIOD = Duration.ofHours(24);
    private static final AiResponseType TYPE = AiResponseType.HOME_PREVIEW;
    private static final AiResponseKey.Input INPUT = new AiResponseKey.Input(
        null, 5L, 36, null, null, null, null, List.of(3L, 1L, 2L), List.of(), 1);
    private static final AiResponseKey KEY = AiResponseKey.of(INPUT);

    @Mock
    private AiResponseStore store;

    @Mock
    private AiCallQuota quota;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private AiResponseService service;

    @BeforeEach
    void setUp() {
        ObservationRegistry observations = ObservationRegistry.create();
        observations.observationConfig().observationHandler(new DefaultMeterObservationHandler(meterRegistry));
        service = new AiResponseService(store, quota, jsonMapper, CLOCK, DAILY_LIMIT, CALL_TIMEOUTS,
            Duration.ofSeconds(10), REUSE_PERIOD, new RecommendAiMetrics(meterRegistry, observations));
    }

    @Test
    @DisplayName("저장한 응답을 쓰면 기능 이름으로 요청 결과 stored와 저장 답 조회 hit를 하나씩 세고, AI 호출 시간은 재지 않는다")
    void countStoredAndLookupHit() {
        given(store.find(TYPE, KEY))
            .willReturn(Optional.of("{\"productIds\":[1,2],\"reason\":\"저장된 답\"}"));

        service.respond(TYPE, INPUT, Answer.class, this::freshAnswerCall);

        assertThat(count("kidly.recommend.ai.requests", "stored")).isEqualTo(1);
        assertThat(count("kidly.recommend.ai.store.lookups", "hit")).isEqualTo(1);
        assertThat(meterRegistry.find("kidly.recommend.ai.call").timers()).isEmpty();
    }

    @Test
    @DisplayName("저장한 응답이 없어 AI가 답하면 요청 결과 answered와 저장 답 조회 miss를 세고, 기능 이름을 붙여 AI 호출 시간을 잰다")
    void countAnsweredAndTimeCall() {
        given(store.find(TYPE, KEY)).willReturn(Optional.empty());
        given(quota.tryAcquire(NOW.toLocalDate(), DAILY_LIMIT)).willReturn(true);

        service.respond(TYPE, INPUT, Answer.class, this::freshAnswerCall);

        assertThat(count("kidly.recommend.ai.requests", "answered")).isEqualTo(1);
        assertThat(count("kidly.recommend.ai.store.lookups", "miss")).isEqualTo(1);
        assertThat(meterRegistry.get("kidly.recommend.ai.call").tag("feature", "home_preview").timer().count())
            .isEqualTo(1);
    }

    enum Failure { CALL_THROWS, CALL_RETURNS_NULL, QUOTA_THROWS }

    @ParameterizedTest
    @EnumSource(Failure.class)
    @DisplayName("AI 호출이 실패하거나 null을 돌려주거나 호출 횟수를 세지 못하면 요청 결과 error를 센다")
    void countError(Failure failure) {
        given(store.find(TYPE, KEY)).willReturn(Optional.empty());
        Supplier<Answer> call = this::freshAnswerCall;
        if (failure == Failure.QUOTA_THROWS) {
            given(quota.tryAcquire(NOW.toLocalDate(), DAILY_LIMIT)).willThrow(new QueryTimeoutException("느림"));
        } else {
            given(quota.tryAcquire(NOW.toLocalDate(), DAILY_LIMIT)).willReturn(true);
            call = failure == Failure.CALL_THROWS ? () -> { throw new IllegalStateException("모델 오류"); } : () -> null;
        }

        service.respond(TYPE, INPUT, Answer.class, call);

        assertThat(count("kidly.recommend.ai.requests", "error")).isEqualTo(1);
    }

    @Test
    @DisplayName("하루 호출 상한에 걸리면 요청 결과 daily_limit을 세고 error는 세지 않는다")
    void countDailyLimit() {
        given(store.find(TYPE, KEY)).willReturn(Optional.empty());
        given(quota.tryAcquire(NOW.toLocalDate(), DAILY_LIMIT)).willReturn(false);

        service.respond(TYPE, INPUT, Answer.class, this::freshAnswerCall);

        assertThat(count("kidly.recommend.ai.requests", "daily_limit")).isEqualTo(1);
        assertThat(count("kidly.recommend.ai.requests", "error")).isZero();
    }

    @Test
    @DisplayName("저장소가 저장 답을 읽지 못하면 저장 답 조회 error를 세고, miss는 세지 않는다")
    void countLookupError() {
        given(store.find(TYPE, KEY)).willThrow(new QueryTimeoutException("Redis 시간 초과"));
        given(quota.tryAcquire(NOW.toLocalDate(), DAILY_LIMIT)).willReturn(true);

        service.respond(TYPE, INPUT, Answer.class, this::freshAnswerCall);

        assertThat(count("kidly.recommend.ai.store.lookups", "error")).isEqualTo(1);
        assertThat(count("kidly.recommend.ai.store.lookups", "miss")).isZero();
    }

    // TYPE(홈 맛보기)의 그 결과 카운터 값이다
    private double count(String name, String result) {
        return meterRegistry.get(name).tag("feature", "home_preview").tag("result", result).counter().count();
    }

    @Test
    @DisplayName("재사용 기간 안에 같은 종류 · 키로 저장한 응답이 있으면 그 응답을 돌려주고, AI를 부르지 않고 횟수도 세지 않는다")
    void reuseStoredResponseWithoutCallingAi() {
        given(store.find(TYPE, KEY))
            .willReturn(Optional.of("{\"productIds\":[1,2],\"reason\":\"저장된 답\"}"));
        AtomicInteger calls = new AtomicInteger();

        Optional<Answer> answer = service.respond(TYPE, INPUT, Answer.class, countingCall(calls, freshAnswer()));

        assertThat(answer).contains(new Answer(List.of(1L, 2L), "저장된 답"));
        assertThat(calls).hasValue(0);
        verify(quota, never()).tryAcquire(any(), anyInt());
        verify(store, never()).save(any(), any(), any(), any());
    }

    @Test
    @DisplayName("저장한 응답이 없으면 한국 시간 날짜로 횟수를 세고, AI를 불러, 같은 종류 · 키 · 지금 시각으로 저장한 뒤 돌려준다")
    void callAiAndSaveWhenNothingStored() {
        given(store.find(TYPE, KEY)).willReturn(Optional.empty());
        given(quota.tryAcquire(LocalDate.of(2026, 11, 11), DAILY_LIMIT)).willReturn(true);
        AtomicInteger calls = new AtomicInteger();

        Optional<Answer> answer = service.respond(TYPE, INPUT, Answer.class, countingCall(calls, freshAnswer()));

        assertThat(answer).contains(freshAnswer());
        assertThat(calls).hasValue(1);
        ArgumentCaptor<String> savedJson = ArgumentCaptor.forClass(String.class);
        verify(store, timeout(1_000)).save(eq(TYPE), eq(KEY), savedJson.capture(), eq(REUSE_PERIOD));
        assertThat(jsonMapper.readValue(savedJson.getValue(), Answer.class)).isEqualTo(freshAnswer());
    }

    @Test
    @DisplayName("AI 답을 저장소에 쓰는 동안 기다리지 않고 바로 돌려준다(저장소가 느려도 응답이 늦어지지 않는다)")
    void returnWithoutWaitingForSave() {
        given(store.find(TYPE, KEY)).willReturn(Optional.empty());
        given(quota.tryAcquire(any(), anyInt())).willReturn(true);
        CountDownLatch release = new CountDownLatch(1);
        willAnswer(invocation -> release.await(5, TimeUnit.SECONDS)).given(store).save(any(), any(), any(), any());

        long startedAt = System.nanoTime();
        Optional<Answer> answer = service.respond(TYPE, INPUT, Answer.class, countingCall(new AtomicInteger(),
            freshAnswer()));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
        release.countDown();

        assertThat(answer).contains(freshAnswer());
        assertThat(elapsed).isLessThan(Duration.ofSeconds(1));
        verify(store, timeout(1_000)).save(eq(TYPE), eq(KEY), any(), eq(REUSE_PERIOD));
    }

    @Test
    @DisplayName("하루 상한에 닿았으면 AI를 부르지 않고 AI 결과 없음을 돌려준다")
    void returnNothingWhenDailyLimitReached() {
        given(store.find(TYPE, KEY)).willReturn(Optional.empty());
        given(quota.tryAcquire(LocalDate.of(2026, 11, 11), DAILY_LIMIT)).willReturn(false);
        AtomicInteger calls = new AtomicInteger();

        Optional<Answer> answer = service.respond(TYPE, INPUT, Answer.class, countingCall(calls, freshAnswer()));

        assertThat(answer).isEmpty();
        assertThat(calls).hasValue(0);
        verify(store, never()).save(any(), any(), any(), any());
    }

    @Test
    @DisplayName("AI 호출이 예외를 던지면 예외를 밖으로 던지지 않고 AI 결과 없음을 돌려주며, 저장하지 않고 로그에 예외 메시지를 남기지 않는다")
    void returnNothingWhenAiCallFails() {
        given(store.find(TYPE, KEY)).willReturn(Optional.empty());
        given(quota.tryAcquire(LocalDate.of(2026, 11, 11), DAILY_LIMIT)).willReturn(true);
        ListAppender<ILoggingEvent> logs = attachLogs();

        Optional<Answer> answer;
        try {
            answer = service.respond(TYPE, INPUT, Answer.class, () -> {
                throw new IllegalStateException("응답 본문 {\"secret\":1}");
            });
        } finally {
            detachLogs(logs);
        }

        assertThat(answer).isEmpty();
        verify(store, never()).save(any(), any(), any(), any());
        assertThat(logs.list).isNotEmpty();
        assertThat(logs.list).allSatisfy(event -> {
            assertThat(event.getFormattedMessage()).doesNotContain("응답 본문");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    @Test
    @DisplayName("AI가 null을 돌려주면 AI 결과 없음을 돌려주고 저장하지 않는다")
    void returnNothingWhenAiReturnsNull() {
        given(store.find(TYPE, KEY)).willReturn(Optional.empty());
        given(quota.tryAcquire(LocalDate.of(2026, 11, 11), DAILY_LIMIT)).willReturn(true);

        Optional<Answer> answer = service.respond(TYPE, INPUT, Answer.class, () -> null);

        assertThat(answer).isEmpty();
        verify(store, never()).save(any(), any(), any(), any());
    }

    @Test
    @DisplayName("응답 저장이 실패해도 이미 받은 AI 답을 돌려주고, 로그에 예외 메시지를 남기지 않는다")
    void returnAnswerEvenWhenSavingFails() {
        given(store.find(TYPE, KEY)).willReturn(Optional.empty());
        given(quota.tryAcquire(LocalDate.of(2026, 11, 11), DAILY_LIMIT)).willReturn(true);
        willThrow(new DataIntegrityViolationException("Failing row contains (비밀 값)"))
            .given(store).save(eq(TYPE), eq(KEY), any(), eq(REUSE_PERIOD));
        ListAppender<ILoggingEvent> logs = attachLogs();

        Optional<Answer> answer;
        try {
            answer = service.respond(TYPE, INPUT, Answer.class, this::freshAnswerCall);
            // 저장은 다른 스레드에서 하므로 저장 실패 로그가 남을 때까지 기다린다
            waitUntilLogged(logs);
        } finally {
            detachLogs(logs);
        }

        assertThat(answer).contains(freshAnswer());
        assertThat(logs.list).isNotEmpty();
        assertThat(logs.list).allSatisfy(event -> {
            assertThat(event.getFormattedMessage()).doesNotContain("비밀 값");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    @Test
    @DisplayName("저장한 응답을 지금 응답 형식으로 읽지 못하면 저장한 응답이 없는 것으로 보고 AI를 다시 불러 저장한다")
    void callAiAgainWhenStoredResponseDoesNotMatchType() {
        given(store.find(TYPE, KEY)).willReturn(Optional.of("[1,2,3]"));
        given(quota.tryAcquire(LocalDate.of(2026, 11, 11), DAILY_LIMIT)).willReturn(true);

        Optional<Answer> answer = service.respond(TYPE, INPUT, Answer.class, this::freshAnswerCall);

        assertThat(answer).contains(freshAnswer());
        verify(store, timeout(1_000)).save(eq(TYPE), eq(KEY), any(), eq(REUSE_PERIOD));
    }

    @Test
    @DisplayName("저장한 응답을 찾다 DB 오류가 나면 저장한 응답이 없는 것으로 보고 AI를 불러 답을 돌려준다")
    void callAiWhenFindingStoredResponseFails() {
        given(store.find(TYPE, KEY))
            .willThrow(new QueryTimeoutException("조회 시간 초과"));
        given(quota.tryAcquire(LocalDate.of(2026, 11, 11), DAILY_LIMIT)).willReturn(true);

        Optional<Answer> answer = service.respond(TYPE, INPUT, Answer.class, this::freshAnswerCall);

        assertThat(answer).contains(freshAnswer());
    }

    @Test
    @DisplayName("하루 횟수를 세다 DB 오류가 나면 셀 수 없으므로 AI를 부르지 않고 AI 결과 없음을 돌려준다")
    void returnNothingWhenCountingCallsFails() {
        given(store.find(TYPE, KEY)).willReturn(Optional.empty());
        given(quota.tryAcquire(LocalDate.of(2026, 11, 11), DAILY_LIMIT))
            .willThrow(new QueryTimeoutException("횟수 갱신 시간 초과"));
        AtomicInteger calls = new AtomicInteger();

        Optional<Answer> answer = service.respond(TYPE, INPUT, Answer.class, countingCall(calls, freshAnswer()));

        assertThat(answer).isEmpty();
        assertThat(calls).hasValue(0);
    }

    @Test
    @DisplayName("트랜잭션 안에서 부르면 하루 횟수 줄이 AI 응답을 기다리는 동안 잠기므로, 아무것도 하지 않고 IllegalStateException을 던진다")
    void rejectCallInsideTransaction() {
        AtomicInteger calls = new AtomicInteger();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        try {
            assertThatThrownBy(() -> service.respond(TYPE, INPUT, Answer.class, countingCall(calls, freshAnswer())))
                .isInstanceOf(IllegalStateException.class);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }

        assertThat(calls).hasValue(0);
        verifyNoInteractions(store, quota);
    }

    private Answer freshAnswerCall() {
        return freshAnswer();
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void waitUntilLogged(ListAppender<ILoggingEvent> logs) {
        long deadline = System.nanoTime() + Duration.ofSeconds(1).toNanos();
        while (logs.list.isEmpty() && System.nanoTime() < deadline) {
            sleep(Duration.ofMillis(10));
        }
    }

    private static ListAppender<ILoggingEvent> attachLogs() {
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(AiResponseService.class)).addAppender(logs);
        return logs;
    }

    private static void detachLogs(ListAppender<ILoggingEvent> logs) {
        ((Logger) LoggerFactory.getLogger(AiResponseService.class)).detachAppender(logs);
    }

    private static Answer freshAnswer() {
        return new Answer(List.of(7L), "새로 부른 답");
    }

    private static Supplier<Answer> countingCall(AtomicInteger calls, Answer answer) {
        return () -> {
            calls.incrementAndGet();
            return answer;
        };
    }

    record Answer(List<Long> productIds, String reason) {
    }
}
