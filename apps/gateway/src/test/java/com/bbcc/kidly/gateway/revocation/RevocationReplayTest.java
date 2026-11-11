package com.bbcc.kidly.gateway.revocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.gateway.support.AsyncWait;
import java.time.Duration;
import java.util.List;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.AvailabilityState;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.kafka.event.ConsumerStartingEvent;
import org.springframework.kafka.event.ListenerContainerIdleEvent;
import org.springframework.kafka.listener.MessageListenerContainer;

class RevocationReplayTest {

    private static final String LISTENER = AuthTokensRevokedListener.LISTENER_ID + "-0";
    private static final List<TopicPartition> PARTITIONS = List.of(
        new TopicPartition("kidly.auth.events", 0), new TopicPartition("kidly.auth.events", 1));

    private final RevocationReplay replay = new RevocationReplay(Duration.ofMinutes(10));
    private final RevocationAwareApplicationAvailability availability =
        new RevocationAwareApplicationAvailability(replay);

    @AfterEach
    void stopTimer() {
        replay.destroy();
    }

    @Test
    @DisplayName("다 읽기 전에는 Boot 가 ACCEPTING_TRAFFIC 을 알려도 요청을 받지 않는다. liveness 는 그대로다")
    void refusesUntilCaughtUp() {
        accept(ReadinessState.ACCEPTING_TRAFFIC);
        accept(LivenessState.CORRECT);

        assertThat(availability.getReadinessState()).isEqualTo(ReadinessState.REFUSING_TRAFFIC);
        assertThat(availability.getLivenessState()).isEqualTo(LivenessState.CORRECT);

        replay.onIdle(idle(LISTENER, PARTITIONS));

        assertThat(availability.getReadinessState()).isEqualTo(ReadinessState.ACCEPTING_TRAFFIC);
    }

    @Test
    @DisplayName("다 읽은 뒤에도 Boot 가 알린 상태를 따른다(종료 때 REFUSING_TRAFFIC)")
    void followsBootAfterCaughtUp() {
        replay.onIdle(idle(LISTENER, PARTITIONS));
        accept(ReadinessState.ACCEPTING_TRAFFIC);
        accept(ReadinessState.REFUSING_TRAFFIC);

        assertThat(availability.getReadinessState()).isEqualTo(ReadinessState.REFUSING_TRAFFIC);
    }

    @Test
    @DisplayName("파티션을 받기 전의 idle 이나 다른 리스너의 idle 로는 다 읽은 것으로 보지 않는다")
    void ignoresIdleWithoutPartitionsOrOtherListener() {
        replay.onIdle(idle(LISTENER, List.of()));
        replay.onIdle(idle("otherListener-0", PARTITIONS));

        assertThat(replay.caughtUp()).isFalse();
    }

    @Test
    @DisplayName("리스너가 다시 시작하면 다시 다 읽을 때까지 요청을 받지 않는다")
    void waitsAgainWhenListenerRestarts() {
        replay.onIdle(idle(LISTENER, PARTITIONS));

        replay.onConsumerStarting(starting(LISTENER));

        assertThat(replay.caughtUp()).isFalse();
        replay.onIdle(idle(LISTENER, PARTITIONS));
        assertThat(replay.caughtUp()).isTrue();
    }

    @Test
    @DisplayName("시간 안에 다 읽지 못하면 요청을 받는다")
    void acceptsAfterTimeout() {
        var shortReplay = new RevocationReplay(Duration.ofMillis(100));
        try {
            shortReplay.onConsumerStarting(starting(LISTENER));

            AsyncWait.until(Duration.ofSeconds(5), shortReplay::caughtUp);
        } finally {
            shortReplay.destroy();
        }
    }

    @Test
    @DisplayName("앞 시작의 시간 상한은 다시 시작한 리스너를 Ready 로 만들지 않는다")
    void staleTimeoutDoesNotApplyToRestart() throws InterruptedException {
        var shortReplay = new RevocationReplay(Duration.ofMillis(1000));
        try {
            shortReplay.onConsumerStarting(starting(LISTENER));
            Thread.sleep(500);
            shortReplay.onConsumerStarting(starting(LISTENER));

            // 첫 시작의 상한(1초)은 지났지만 두 번째 시작의 상한(1.5초)은 아직이다
            Thread.sleep(700);
            assertThat(shortReplay.caughtUp()).isFalse();
            AsyncWait.until(Duration.ofSeconds(5), shortReplay::caughtUp);
        } finally {
            shortReplay.destroy();
        }
    }

    private void accept(AvailabilityState state) {
        availability.onApplicationEvent(new AvailabilityChangeEvent<>(this, state));
    }

    private static ListenerContainerIdleEvent idle(String listenerId, List<TopicPartition> partitions) {
        return new ListenerContainerIdleEvent(new Object(), new Object(), 1000L, listenerId, partitions, null, false);
    }

    private static ConsumerStartingEvent starting(String listenerId) {
        var container = mock(MessageListenerContainer.class);
        when(container.getListenerId()).thenReturn(listenerId);
        return new ConsumerStartingEvent(container, container);
    }
}
