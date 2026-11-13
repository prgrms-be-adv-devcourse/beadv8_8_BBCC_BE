package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.bbcc.kidly.boundedcontext.recommend.app.ai.AiCallUsageGauge;
import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.AiCallQuota;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;

@ExtendWith(MockitoExtension.class)
class AiCallUsageGaugeTest {

    // UTC 11-10 15:30은 한국 시간 11-11 00:30이다
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T15:30:00Z"), ZoneId.of("Asia/Seoul"));

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Mock
    private AiCallQuota quota;

    @Test
    @DisplayName("오늘 AI 호출 사용량은 한국 시간 날짜로 하루 호출 횟수 표에서 읽는다")
    void readTodayUsageInSeoul() {
        given(quota.usedOn(LocalDate.of(2026, 11, 11))).willReturn(37);
        new AiCallUsageGauge(registry, quota, CLOCK);

        assertThat(registry.get("kidly.recommend.ai.daily.calls").gauge().value()).isEqualTo(37);
    }

    @Test
    @DisplayName("사용량을 읽지 못하면 0이 아니라 값 없음(NaN)으로 둔다")
    void notANumberWhenUsageCannotBeRead() {
        given(quota.usedOn(LocalDate.of(2026, 11, 11))).willThrow(new QueryTimeoutException("DB 지연"));
        new AiCallUsageGauge(registry, quota, CLOCK);

        assertThat(registry.get("kidly.recommend.ai.daily.calls").gauge().value()).isNaN();
    }
}
