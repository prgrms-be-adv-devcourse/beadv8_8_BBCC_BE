package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class AiResponseServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    // UTC 11-10 15:30은 한국 시간 11-11 00:30이다
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T15:30:00Z"), SEOUL);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 11, 0, 30);
    private static final int DAILY_LIMIT = 2_000;
    private static final Duration CALL_TIMEOUT = Duration.ofMillis(300);
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

    private AiResponseService service;

    @BeforeEach
    void setUp() {
        service = new AiResponseService(store, quota, jsonMapper, CLOCK, DAILY_LIMIT, CALL_TIMEOUT, REUSE_PERIOD);
    }

    @Test
    @DisplayName("재사용 기간 안에 같은 종류 · 키로 저장한 응답이 있으면 그 응답을 돌려주고, AI를 부르지 않고 횟수도 세지 않는다")
    void reuseStoredResponseWithoutCallingAi() {
        given(store.findLatestSince(TYPE, KEY, NOW.minus(REUSE_PERIOD)))
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
        given(store.findLatestSince(TYPE, KEY, NOW.minus(REUSE_PERIOD))).willReturn(Optional.empty());
        given(quota.tryAcquire(LocalDate.of(2026, 11, 11), DAILY_LIMIT)).willReturn(true);
        AtomicInteger calls = new AtomicInteger();

        Optional<Answer> answer = service.respond(TYPE, INPUT, Answer.class, countingCall(calls, freshAnswer()));

        assertThat(answer).contains(freshAnswer());
        assertThat(calls).hasValue(1);
        ArgumentCaptor<String> savedJson = ArgumentCaptor.forClass(String.class);
        verify(store).save(eq(TYPE), eq(KEY), savedJson.capture(), eq(NOW));
        assertThat(jsonMapper.readValue(savedJson.getValue(), Answer.class)).isEqualTo(freshAnswer());
    }

    @Test
    @DisplayName("하루 상한에 닿았으면 AI를 부르지 않고 AI 결과 없음을 돌려준다")
    void returnNothingWhenDailyLimitReached() {
        given(store.findLatestSince(TYPE, KEY, NOW.minus(REUSE_PERIOD))).willReturn(Optional.empty());
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
        given(store.findLatestSince(TYPE, KEY, NOW.minus(REUSE_PERIOD))).willReturn(Optional.empty());
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
    @DisplayName("AI 호출이 제한 시간을 넘기면 끝나기를 기다리지 않고 AI 결과 없음을 돌려주며 저장하지 않는다")
    void returnNothingWhenAiCallTimesOut() {
        given(store.findLatestSince(TYPE, KEY, NOW.minus(REUSE_PERIOD))).willReturn(Optional.empty());
        given(quota.tryAcquire(LocalDate.of(2026, 11, 11), DAILY_LIMIT)).willReturn(true);
        long startedAt = System.nanoTime();

        Optional<Answer> answer = service.respond(TYPE, INPUT, Answer.class, () -> {
            sleep(Duration.ofSeconds(3));
            return freshAnswer();
        });

        assertThat(answer).isEmpty();
        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofMillis(1_500));
        verify(store, never()).save(any(), any(), any(), any());
    }

    @Test
    @DisplayName("AI가 null을 돌려주면 AI 결과 없음을 돌려주고 저장하지 않는다")
    void returnNothingWhenAiReturnsNull() {
        given(store.findLatestSince(TYPE, KEY, NOW.minus(REUSE_PERIOD))).willReturn(Optional.empty());
        given(quota.tryAcquire(LocalDate.of(2026, 11, 11), DAILY_LIMIT)).willReturn(true);

        Optional<Answer> answer = service.respond(TYPE, INPUT, Answer.class, () -> null);

        assertThat(answer).isEmpty();
        verify(store, never()).save(any(), any(), any(), any());
    }

    @Test
    @DisplayName("응답 저장이 실패해도 이미 받은 AI 답을 돌려주고, 로그에 예외 메시지를 남기지 않는다")
    void returnAnswerEvenWhenSavingFails() {
        given(store.findLatestSince(TYPE, KEY, NOW.minus(REUSE_PERIOD))).willReturn(Optional.empty());
        given(quota.tryAcquire(LocalDate.of(2026, 11, 11), DAILY_LIMIT)).willReturn(true);
        willThrow(new DataIntegrityViolationException("Failing row contains (비밀 값)"))
            .given(store).save(eq(TYPE), eq(KEY), any(), eq(NOW));
        ListAppender<ILoggingEvent> logs = attachLogs();

        Optional<Answer> answer;
        try {
            answer = service.respond(TYPE, INPUT, Answer.class, this::freshAnswerCall);
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
        given(store.findLatestSince(TYPE, KEY, NOW.minus(REUSE_PERIOD))).willReturn(Optional.of("[1,2,3]"));
        given(quota.tryAcquire(LocalDate.of(2026, 11, 11), DAILY_LIMIT)).willReturn(true);

        Optional<Answer> answer = service.respond(TYPE, INPUT, Answer.class, this::freshAnswerCall);

        assertThat(answer).contains(freshAnswer());
        verify(store).save(eq(TYPE), eq(KEY), any(), eq(NOW));
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
