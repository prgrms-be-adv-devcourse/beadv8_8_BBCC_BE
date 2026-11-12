package com.bbcc.kidly.boundedcontext.activity.in.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.activity.app.CommunityEventMetrics;
import com.bbcc.kidly.boundedcontext.activity.app.CommunityEventRelayService;
import com.bbcc.kidly.boundedcontext.activity.app.CommunityEventRelayService.RelayRun;
import com.bbcc.kidly.boundedcontext.activity.app.port.EventOutbox;
import com.bbcc.kidly.boundedcontext.support.MutableTestClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 릴레이 스케줄러: 팀 보내기 스위치(kidly.kafka.producer.enabled)가 꺼지면 없고, 값이 없거나 true면 뜬다. 마지막 성공 시각은 기동
 * 시각으로 시작해 끝까지 돈 주기에만 바뀐다(건너뜀 · 예외는 그대로).
 */
class CommunityEventRelaySchedulerTest {

    private static final Instant START = Instant.parse("2026-11-12T01:00:00Z");

    @ParameterizedTest(name = "producer.enabled={0} → 스케줄러 {1}")
    @CsvSource({"'', true", "true, true", "false, false"})
    @DisplayName("보내기 스위치가 꺼지면 릴레이 스케줄러가 없다(기본은 켜짐)")
    void followsProducerSwitch(String enabled, boolean expected) {
        ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CommunityEventRelayScheduler.class)
            .withBean(CommunityEventRelayService.class, () -> mock(CommunityEventRelayService.class))
            .withBean(CommunityEventMetrics.class, () -> mock(CommunityEventMetrics.class));
        if (!enabled.isEmpty()) {
            runner = runner.withPropertyValues("kidly.kafka.producer.enabled=" + enabled);
        }

        runner.run(context -> assertThat(context.containsBean("communityEventRelayScheduler")).isEqualTo(expected));
    }

    @Test
    @DisplayName("마지막 성공 시각: 처음 값은 기동 시각, 끝까지 돌면 그 시각, 건너뜀 · 예외면 그대로")
    void lastSuccess() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MutableTestClock clock = new MutableTestClock(START);
        CommunityEventMetrics metrics = new CommunityEventMetrics(registry, mock(EventOutbox.class), clock);
        CommunityEventRelayService service = mock(CommunityEventRelayService.class);
        CommunityEventRelayScheduler scheduler = new CommunityEventRelayScheduler(service, metrics);
        assertThat(lastSuccess(registry)).isEqualTo(START.getEpochSecond());

        clock.set(START.plusSeconds(5));
        when(service.relay()).thenReturn(new RelayRun(0, 0, 0, true));
        scheduler.relay();
        assertThat(lastSuccess(registry)).isEqualTo(START.getEpochSecond());
        when(service.relay()).thenThrow(new IllegalStateException("db down"));
        scheduler.relay();
        assertThat(lastSuccess(registry)).isEqualTo(START.getEpochSecond());

        doReturn(new RelayRun(1, 0, 0, false)).when(service).relay();
        scheduler.relay();
        assertThat(lastSuccess(registry)).isEqualTo(START.plusSeconds(5).getEpochSecond());
    }

    private static double lastSuccess(SimpleMeterRegistry registry) {
        return registry.get(CommunityEventMetrics.SCHEDULER_LAST_SUCCESS).tag("task", "event_relay").gauge().value();
    }
}
