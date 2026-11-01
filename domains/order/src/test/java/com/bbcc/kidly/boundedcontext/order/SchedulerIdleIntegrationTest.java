package com.bbcc.kidly.boundedcontext.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 쿼리 수 테스트의 측정 전 대기 도우미(#416). 공유 스케줄러에 작업을 넣으므로 래치 해제 · 작업 취소를 finally에서 하고,
 * 끝난 뒤 넣은 작업이 큐에 남지 않았는지 확인한다(다른 테스트에 영향 없게).
 */
@IntegrationTest
class SchedulerIdleIntegrationTest {

    @Autowired
    private ThreadPoolTaskScheduler taskScheduler;

    @Test
    @DisplayName("스케줄러가 작업을 실행 중이면 기다리다 제한 시간에 원인(실행 중 수)과 함께 실패하고, 작업이 끝나면 바로 돌아온다")
    void waitsWhileRunning() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ScheduledFuture<?> blocker = taskScheduler.schedule(() -> block(started, release), Instant.now());
        try {
            assertThat(started.await(10, TimeUnit.SECONDS)).as("막아 둔 작업이 시작됨").isTrue();

            assertThatThrownBy(() -> SchedulerIdle.await(taskScheduler, Duration.ofMillis(500)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("실행 중 1");

            release.countDown();
            SchedulerIdle.await(taskScheduler, Duration.ofSeconds(10));
            assertThat(blocker.isDone()).as("막아 둔 작업이 끝남").isTrue();
        } finally {
            cleanUp(release, List.of(blocker));
        }
    }

    @Test
    @DisplayName("실행 중 작업 뒤에 지금 실행할 차례인 작업이 큐에 있으면 그 수도 원인으로 보이고, 둘 다 끝나야 돌아온다")
    void waitsWhileDueTaskQueued() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch queuedRan = new CountDownLatch(1);
        ScheduledFuture<?> blocker = taskScheduler.schedule(() -> block(started, release), Instant.now());
        ScheduledFuture<?> queued = taskScheduler.schedule(queuedRan::countDown, Instant.now());
        try {
            assertThat(started.await(10, TimeUnit.SECONDS)).as("막아 둔 작업이 시작됨").isTrue();

            assertThatThrownBy(() -> SchedulerIdle.await(taskScheduler, Duration.ofMillis(500)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("실행할 차례 1");

            release.countDown();
            SchedulerIdle.await(taskScheduler, Duration.ofSeconds(10));
            assertThat(queuedRan.getCount()).as("큐에 있던 작업까지 실행된 뒤에 돌아옴").isZero();
            assertThat(queued.isDone()).as("큐에 있던 작업이 끝남").isTrue();
        } finally {
            cleanUp(release, List.of(blocker, queued));
        }
    }

    private static void block(CountDownLatch started, CountDownLatch release) {
        started.countDown();
        try {
            if (!release.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("해제 신호를 기다리다 시간 초과");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void cleanUp(CountDownLatch release, List<ScheduledFuture<?>> futures) {
        release.countDown();
        futures.forEach(future -> future.cancel(true));
        // 취소한 작업은 실행 차례가 올 때까지 큐에 남으므로 바로 걷어 낸다(이미 끝났거나 취소된 작업만 지움)
        taskScheduler.getScheduledThreadPoolExecutor().purge();
        assertThat(taskScheduler.getScheduledThreadPoolExecutor().getQueue())
            .as("넣은 작업이 공유 스케줄러 큐에 남지 않음")
            .doesNotContainAnyElementsOf(futures.stream().map(Runnable.class::cast).toList());
    }
}
