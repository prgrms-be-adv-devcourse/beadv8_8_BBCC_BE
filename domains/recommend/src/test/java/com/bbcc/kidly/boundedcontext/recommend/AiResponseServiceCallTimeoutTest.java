package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI 공통 서비스가 AI 답을 얼마나 기다리는지와, 기다리지 못한 답을 어떻게 다루는지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class AiResponseServiceCallTimeoutTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T15:30:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 11, 0, 30);
    private static final int DAILY_LIMIT = 2_000;
    private static final Duration REUSE_PERIOD = Duration.ofHours(24);
    private static final AiResponseKey.Input INPUT = new AiResponseKey.Input(
        null, 5L, 36, null, null, null, null, List.of(3L, 1L, 2L), List.of(), 1);

    @Mock
    private AiResponseStore store;

    @Mock
    private AiCallQuota quota;

    private AiResponseService service;

    @BeforeEach
    void setUp() {
        Map<AiResponseType, Duration> callTimeouts = Map.of(
            AiResponseType.HOME_PREVIEW, Duration.ofMillis(200),
            AiResponseType.BOUGHT_TOGETHER, Duration.ofMillis(1_500),
            AiResponseType.SIZE_RECOMMENDATION, Duration.ofMillis(1_500));
        service = new AiResponseService(store, quota, JsonMapper.builder().build(), CLOCK, DAILY_LIMIT, callTimeouts,
            REUSE_PERIOD);
    }

    @Test
    @DisplayName("AI 답을 기다리는 시간은 종류마다 다르다. 같은 시간이 걸린 답도 제한 시간이 긴 종류에서만 받는다")
    void waitForAiAnswerByType() {
        given(store.findLatestSince(any(), any(), any())).willReturn(Optional.empty());
        given(quota.tryAcquire(any(), anyInt())).willReturn(true);

        Optional<Answer> homePreview = service.respond(AiResponseType.HOME_PREVIEW, INPUT, Answer.class,
            () -> answerAfter(Duration.ofMillis(600)));
        Optional<Answer> boughtTogether = service.respond(AiResponseType.BOUGHT_TOGETHER, INPUT, Answer.class,
            () -> answerAfter(Duration.ofMillis(600)));

        assertThat(homePreview).isEmpty();
        assertThat(boughtTogether).contains(new Answer("늦은 답"));
    }

    @Test
    @DisplayName("AI 답이 제한 시간을 넘기면 기다리지 않고 AI 결과 없음을 돌려주고, 늦게 온 답은 받는 대로 저장해 다음 요청에서 쓴다")
    void saveLateAnswerAfterTimeout() {
        given(store.findLatestSince(any(), any(), any())).willReturn(Optional.empty());
        given(quota.tryAcquire(any(), anyInt())).willReturn(true);
        long startedAt = System.nanoTime();

        Optional<Answer> answer = service.respond(AiResponseType.HOME_PREVIEW, INPUT, Answer.class,
            () -> answerAfter(Duration.ofMillis(600)));

        assertThat(answer).isEmpty();
        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofMillis(500));
        verify(store, timeout(3_000)).save(AiResponseType.HOME_PREVIEW, AiResponseKey.of(INPUT),
            "{\"reason\":\"늦은 답\"}", NOW);
    }

    private static Answer answerAfter(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        return new Answer("늦은 답");
    }

    record Answer(String reason) {
    }
}
