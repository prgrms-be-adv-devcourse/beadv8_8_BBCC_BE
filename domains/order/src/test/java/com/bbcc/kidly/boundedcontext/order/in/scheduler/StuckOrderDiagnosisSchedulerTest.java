package com.bbcc.kidly.boundedcontext.order.in.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.bbcc.kidly.boundedcontext.order.CapturedLogs;
import com.bbcc.kidly.boundedcontext.order.app.OrderOperationMetrics;
import com.bbcc.kidly.boundedcontext.order.app.port.SingleServerJobRunner;
import com.bbcc.kidly.boundedcontext.order.app.stuck.StuckOrderDiagnosisService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 멈춘 주문 진단 주기 작업(#926): 스케줄러 스레드는 넘기기만 하고 바로 돌아오며(전용 스레드에서 실행), 앞 실행이 돌면 건너뛰고,
 * 실패해도 예외 종류만 남긴다.
 */
class StuckOrderDiagnosisSchedulerTest {

    private final StuckOrderDiagnosisService service = mock(StuckOrderDiagnosisService.class);
    private final SingleServerJobRunner jobRunner = (name, job) -> {
        job.run();
        return true;
    };
    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final StuckOrderDiagnosisScheduler scheduler =
        new StuckOrderDiagnosisScheduler(service, jobRunner, new OrderOperationMetrics(registry),
            Duration.ofMinutes(5));

    @Test
    @DisplayName("마지막 성공 시각(#1067): 진단 실행이 끝나면 갱신, 예외면 갱신 안 함")
    void lastSuccess() {
        when(service.diagnoseNew(any())).thenThrow(new IllegalStateException("DB 끊김"));
        scheduler.runOnWorker();
        assertThat(lastSuccessValue()).isNaN();

        org.mockito.Mockito.reset(service);
        when(service.diagnoseNew(any())).thenReturn(new StuckOrderDiagnosisService.Run(0, 0, 0, false, false));
        scheduler.runOnWorker();
        assertThat(lastSuccessValue()).isCloseTo(System.currentTimeMillis() / 1000.0, within(5.0));
    }

    private double lastSuccessValue() {
        return registry.get("kidly.order.scheduler.last.success.seconds").tag("job", "stuck_diagnosis").gauge().value();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        scheduler.shutdown();
    }

    @Test
    @DisplayName("스케줄러 스레드는 바로 돌아오고 진단은 전용 스레드(order-stuck-diagnosis)에서 돈다. 앞 실행 중이면 이번 주기는 건너뛴다")
    void runsOnWorkerAndSkipsWhileRunning() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<String> thread = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        when(service.diagnoseNew(any())).thenAnswer(invocation -> {
            calls.incrementAndGet();
            thread.set(Thread.currentThread().getName());
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return new StuckOrderDiagnosisService.Run(0, 0, 0, false, false);
        });

        scheduler.diagnose();
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        scheduler.diagnose();
        release.countDown();

        assertThat(thread.get()).isEqualTo("order-stuck-diagnosis");
        Thread.sleep(200);
        assertThat(calls).hasValue(1);
    }

    @Test
    @DisplayName("진단이 던지면 WARN 한 줄(예외 종류만)을 남기고 다음 실행은 다시 돈다")
    void survivesFailure() {
        when(service.diagnoseNew(any())).thenThrow(new IllegalStateException("주문 정보 홍길동"))
            .thenReturn(new StuckOrderDiagnosisService.Run(1, 1, 0, false, false));

        List<ILoggingEvent> events;
        try (CapturedLogs logs = CapturedLogs.start()) {
            scheduler.runOnWorker();
            scheduler.runOnWorker();
            events = logs.events();
        }

        assertThat(events).anySatisfy(event ->
            assertThat(event.getFormattedMessage()).isEqualTo("멈춘 주문 진단 실행 실패: error=IllegalStateException"));
        assertThat(events).allSatisfy(event -> {
            assertThat(event.getFormattedMessage()).doesNotContain("홍길동");
            assertThat(event.getThrowableProxy()).isNull();
        });
        assertThat(events).anySatisfy(event ->
            assertThat(event.getFormattedMessage()).contains("멈춘 주문 진단: requested=1"));
    }
}
