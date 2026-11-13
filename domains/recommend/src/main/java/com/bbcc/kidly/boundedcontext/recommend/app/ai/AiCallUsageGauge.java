package com.bbcc.kidly.boundedcontext.recommend.app.ai;

import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.AiCallQuota;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 오늘 사용한 AI 호출 횟수를 Prometheus가 읽어 가게 하는 지표다. 하루 상한까지 얼마나 남았는지 보는 데 쓴다.
 */
@Component
public class AiCallUsageGauge {

    private final AiCallQuota quota;
    private final Clock clock;

    @Autowired
    public AiCallUsageGauge(MeterRegistry meterRegistry, AiCallQuota quota) {
        this(meterRegistry, quota, Clock.system(ZoneId.of("Asia/Seoul")));
    }

    public AiCallUsageGauge(MeterRegistry meterRegistry, AiCallQuota quota, Clock clock) {
        this.quota = quota;
        this.clock = clock;
        // 게이지는 대상을 약하게 참조하므로, 살아 있는 빈 자신을 대상으로 등록해 GC로 사라지지 않게 한다
        Gauge.builder(RecommendAiMetrics.DAILY_CALLS, this, AiCallUsageGauge::readUsage)
            .description("오늘(한국 시간) 사용한 추천 AI 호출 횟수. 읽지 못하면 NaN")
            .register(meterRegistry);
    }

    // 읽지 못하면 0이 아니라 NaN으로 둔다. 스크레이프마다 남아 시끄러우므로 로그는 남기지 않는다
    private double readUsage() {
        try {
            return quota.usedOn(LocalDate.now(clock));
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }
}
