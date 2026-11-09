package com.bbcc.kidly.boundedcontext.review.in.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.review.app.CommunityDeadLetterService;
import com.bbcc.kidly.boundedcontext.review.app.CommunityMetrics;
import com.bbcc.kidly.boundedcontext.review.app.ReplicaHealthService;
import com.bbcc.kidly.boundedcontext.review.app.ReviewModerationProperties;
import com.bbcc.kidly.boundedcontext.review.app.port.CommunityDeadLetters;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewModerationStats;
import com.bbcc.kidly.boundedcontext.support.MutableTestClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * DLT 보관 리스너의 지표: 보관이 끝나면(새로 넣음 · 이미 있음) 마지막 보관 시각, 보관이 실패하면 실패 카운터만(시각은 그대로).
 */
class CommunityDeadLetterKafkaListenerMetricsTest {

    private static final Instant NOW = Instant.parse("2026-11-09T01:00:00Z");

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final CommunityMetrics metrics = new CommunityMetrics(registry, mock(CommunityDeadLetters.class),
        mock(ReviewModerationStats.class), new ReviewModerationProperties(false, Duration.ofMinutes(1), 10, 3),
        mock(ReplicaHealthService.class), new MutableTestClock(NOW));
    private final CommunityDeadLetterService service = mock(CommunityDeadLetterService.class);
    private final CommunityDeadLetterKafkaListener listener = new CommunityDeadLetterKafkaListener(service, metrics);

    @Test
    @DisplayName("새로 넣든 이미 있든 보관이 끝나면 마지막 보관 시각이 지금(epoch 초)이고 실패 카운터는 그대로다")
    void storedSetsLastStored() {
        when(service.receive(any())).thenReturn(false);

        listener.on(record(1));

        assertThat(registry.get(CommunityMetrics.DLT_LAST_STORED).gauge().value()).isEqualTo(NOW.getEpochSecond());
        assertThat(registry.get(CommunityMetrics.DLT_STORE_FAILURES).counter().count()).isZero();
    }

    @Test
    @DisplayName("보관이 실패하면 실패 카운터가 늘고(재시도마다) 예외는 그대로 던지며, 마지막 보관 시각은 NaN 그대로다")
    void failureCounts() {
        when(service.receive(any())).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> listener.on(record(1))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> listener.on(record(1))).isInstanceOf(IllegalStateException.class);

        assertThat(registry.get(CommunityMetrics.DLT_STORE_FAILURES).counter().count()).isEqualTo(2.0);
        assertThat(registry.get(CommunityMetrics.DLT_LAST_STORED).gauge().value()).isNaN();
    }

    private static ConsumerRecord<String, String> record(long offset) {
        return new ConsumerRecord<>("kidly.order.events.community.dlt", 0, offset, "1", "{}");
    }
}
