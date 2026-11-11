package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bbcc.kidly.boundedcontext.recommend.app.ai.AiResponseService;
import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.AiCallQuota;
import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.AiResponseStore;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseKey;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
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
        given(store.findLatestSince(any(), any(), any())).willReturn(Optional.of("{\"reason\":\"저장한 답\"}"));
        AtomicInteger calls = new AtomicInteger();

        CompletableFuture<Optional<Answer>> result = service.respondAsync(TYPE, INPUT, Answer.class, () -> {
            calls.incrementAndGet();
            return new Answer("새 답");
        });

        assertThat(result).isCompletedWithValue(Optional.of(new Answer("저장한 답")));
        assertThat(calls).hasValue(0);
        verifyNoInteractions(quota);
    }

    record Answer(String reason) {
    }
}
