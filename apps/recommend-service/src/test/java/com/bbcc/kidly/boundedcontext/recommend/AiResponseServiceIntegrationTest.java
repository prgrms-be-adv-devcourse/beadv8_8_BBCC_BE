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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI 응답 서비스가 실제 PostgreSQL의 응답 표와 하루 횟수 표로 지난 답을 다시 쓰고 호출 횟수를 세는지 확인한다.
 */
@IntegrationTest
class AiResponseServiceIntegrationTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    // 실제 시계를 쓰면 한국 시간 자정을 지나는 순간 하루 횟수 줄의 날짜가 바뀌므로 시계를 고정한다
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-11T03:00:00Z"), SEOUL);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    private static final AiResponseType TYPE = AiResponseType.BOUGHT_TOGETHER;
    private static final AiResponseKey.Input INPUT = new AiResponseKey.Input(
        10L, 5L, 36, null, null, null, null, List.of(1L, 2L, 3L), List.of(7L), 1);

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

        AiResponseService service = fixedClockService();

        Optional<Answer> first = service.respond(TYPE, INPUT, Answer.class, countingCall(calls));
        Optional<Answer> second = service.respond(TYPE, INPUT, Answer.class, countingCall(calls));

        assertThat(first).contains(answer());
        assertThat(second).contains(answer());
        assertThat(calls).hasValue(1);
        assertThat(callCountOn(NOW.toLocalDate())).isEqualTo(1);
    }

    private AiResponseService fixedClockService() {
        return new AiResponseService(
            store, quota, jsonMapper, CLOCK, 2_000, Map.of(TYPE, Duration.ofSeconds(2)), Duration.ofSeconds(10),
            Duration.ofHours(24));
    }

    private Integer callCountOn(LocalDate date) {
        return jdbcTemplate.queryForObject(
            "select call_count from recommend.ai_call_usage where usage_date = ?", Integer.class, date);
    }

    private static Supplier<Answer> countingCall(AtomicInteger calls) {
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
