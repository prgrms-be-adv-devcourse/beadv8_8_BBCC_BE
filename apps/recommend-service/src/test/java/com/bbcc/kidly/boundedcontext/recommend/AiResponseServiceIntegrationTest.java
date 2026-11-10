package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.ai.AiResponseService;
import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.AiCallQuota;
import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.AiResponseStore;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseKey;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseType;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI 응답 서비스가 실제 PostgreSQL의 응답 표와 하루 횟수 표로 지난 답을 다시 쓰고 호출 횟수를 세는지 확인한다.
 */
@IntegrationTest
class AiResponseServiceIntegrationTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final AiResponseType TYPE = AiResponseType.BOUGHT_TOGETHER;
    private static final AiResponseKey.Input INPUT = new AiResponseKey.Input(
        10L, 5L, 36, null, null, null, null, List.of(1L, 2L, 3L), List.of(7L), 1);

    @Autowired
    private AiResponseService service;

    @Autowired
    private AiResponseStore store;

    @Autowired
    private AiCallQuota quota;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("같은 종류 · 같은 입력으로 두 번 부르면 AI는 한 번만 부르고, 두 번째는 저장한 답을 돌려주며, 그날 호출 횟수는 1이다")
    void callAiOnceForSameInput() {
        AtomicInteger calls = new AtomicInteger();

        Optional<Answer> first = service.respond(TYPE, INPUT, Answer.class, countingCall(calls));
        Optional<Answer> second = service.respond(TYPE, INPUT, Answer.class, countingCall(calls));

        assertThat(first).contains(answer());
        assertThat(second).contains(answer());
        assertThat(calls).hasValue(1);
        assertThat(callCountOn(LocalDate.now(SEOUL))).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "1, 1"})
    @DisplayName("재사용 기간(24시간) 경계: 정확히 24시간 전에 저장한 답은 다시 쓰고, 24시간 1초 전에 저장한 답은 다시 쓰지 않고 AI를 부른다")
    void reuseBoundary(long secondsBeyondReusePeriod, int expectedCalls) {
        Clock clock = Clock.fixed(Instant.parse("2026-11-11T03:00:00Z"), SEOUL);
        LocalDateTime now = LocalDateTime.now(clock);
        AiResponseService fixedClockService = new AiResponseService(
            store, quota, jsonMapper, clock, 2_000, Duration.ofSeconds(2), Duration.ofHours(24));
        store.save(TYPE, AiResponseKey.of(INPUT), "{\"productIds\":[9],\"reason\":\"저장한 답\"}",
            now.minusHours(24).minusSeconds(secondsBeyondReusePeriod));
        AtomicInteger calls = new AtomicInteger();

        Optional<Answer> answer = fixedClockService.respond(TYPE, INPUT, Answer.class, countingCall(calls));

        assertThat(calls).hasValue(expectedCalls);
        assertThat(answer).isPresent();
    }

    private Integer callCountOn(LocalDate date) {
        return jdbcTemplate.queryForObject(
            "select call_count from recommend.ai_call_usage where usage_date = ?", Integer.class, date);
    }

    private static java.util.function.Supplier<Answer> countingCall(AtomicInteger calls) {
        return () -> {
            calls.incrementAndGet();
            return answer();
        };
    }

    private static Answer answer() {
        return new Answer(List.of(1L, 2L), "새로 부른 답");
    }

    record Answer(List<Long> productIds, String reason) {
    }
}
