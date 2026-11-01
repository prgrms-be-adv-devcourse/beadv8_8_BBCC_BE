package com.bbcc.kidly.boundedcontext.delivery.in.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.delivery.app.DeliveryTrackingProperties;
import com.bbcc.kidly.boundedcontext.delivery.app.DeliveryTrackingService;
import com.bbcc.kidly.boundedcontext.delivery.app.dto.TrackingTarget;
import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryJobRunner;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * 택배 조회 스케줄러: 비활성이면 건너뛰고, 잠금을 잡은 서버만 돌고, 배송 ID 커서로 이어 읽고, 한 건이 실패해도 다음 건을 처리한다.
 */
@ExtendWith(OutputCaptureExtension.class)
class DeliveryStatusPollerTest {

    private final DeliveryTrackingService trackingService = mock(DeliveryTrackingService.class);
    private final List<String> jobNames = new ArrayList<>();
    private final DeliveryJobRunner runsNow = (jobName, job) -> {
        jobNames.add(jobName);
        job.run();
        return true;
    };

    private DeliveryStatusPoller poller(int batchSize, int maxPerRun, DeliveryJobRunner jobRunner) {
        return new DeliveryStatusPoller(trackingService,
            new DeliveryTrackingProperties(Duration.ofMinutes(10), batchSize, maxPerRun), jobRunner);
    }

    private static TrackingTarget target(long id) {
        return new TrackingTarget(id, null, "n" + id);
    }

    @Test
    @DisplayName("비활성(인증값 없음)이면 잠금도 잡지 않고 대상을 읽지도 않는다")
    void skipsWhenDisabled() {
        given(trackingService.isEnabled()).willReturn(false);

        poller(2, 10, runsNow).trackDeliveries();

        assertThat(jobNames).isEmpty();
        verify(trackingService, never()).findTargets(anyLong(), anyInt());
    }

    @Test
    @DisplayName("다른 서버가 돌고 있으면(잠금 실패) 대상을 읽지 않는다")
    void skipsWhenOtherServerRuns() {
        given(trackingService.isEnabled()).willReturn(true);

        poller(2, 10, (jobName, job) -> false).trackDeliveries();

        verify(trackingService, never()).findTargets(anyLong(), anyInt());
    }

    @Test
    @DisplayName("가득 찬 배치면 마지막 배송 ID 다음부터 이어 읽고, 덜 찬 배치에서 끝난다. 작업 이름은 delivery-tracking")
    void continuesWithCursor() {
        given(trackingService.isEnabled()).willReturn(true);
        given(trackingService.findTargets(0L, 2)).willReturn(List.of(target(1), target(2)));
        given(trackingService.findTargets(2L, 2)).willReturn(List.of(target(5)));

        poller(2, 10, runsNow).trackDeliveries();

        assertThat(jobNames).containsExactly("delivery-tracking");
        verify(trackingService).track(target(1));
        verify(trackingService).track(target(2));
        verify(trackingService).track(target(5));
        verify(trackingService, never()).findTargets(5L, 2);
    }

    @Test
    @DisplayName("한 건이 실패해도 다음 건을 처리한다")
    void continuesAfterFailure() {
        given(trackingService.isEnabled()).willReturn(true);
        given(trackingService.findTargets(0L, 2)).willReturn(List.of(target(1), target(2)));
        given(trackingService.findTargets(2L, 2)).willReturn(List.of());
        doThrow(new IllegalStateException("주입한 실패")).when(trackingService).track(target(1));

        poller(2, 10, runsNow).trackDeliveries();

        verify(trackingService).track(target(2));
    }

    @Test
    @DisplayName("한 실행에서 max-per-run건까지만 처리한다")
    void stopsAtMaxPerRun() {
        given(trackingService.isEnabled()).willReturn(true);
        given(trackingService.findTargets(0L, 2)).willReturn(List.of(target(1), target(2)));
        given(trackingService.findTargets(2L, 1)).willReturn(List.of(target(3)));

        poller(2, 3, runsNow).trackDeliveries();

        verify(trackingService).track(target(3));
        // 상한에 닿으면 남은 대상이 있는지 한 건만 더 본다(#656). 그 대상은 처리하지 않는다
        verify(trackingService).findTargets(3L, 1);
        verify(trackingService, never()).track(target(4));
    }

    /**
     * 배송 ID 1..total이 모두 배송 중으로 남는다고 본다(조회해도 대상에서 빠지지 않음). afterId 다음부터 limit건.
     */
    private List<Long> givenInProgress(long total) {
        TreeSet<Long> ids = new TreeSet<>();
        for (long id = 1; id <= total; id++) {
            ids.add(id);
        }
        List<Long> tracked = new ArrayList<>();
        given(trackingService.isEnabled()).willReturn(true);
        given(trackingService.findTargets(anyLong(), anyInt())).willAnswer(invocation -> {
            long afterId = invocation.getArgument(0);
            int limit = invocation.getArgument(1);
            return ids.tailSet(afterId, false).stream().limit(limit).map(DeliveryStatusPollerTest::target).toList();
        });
        org.mockito.Mockito.doAnswer(invocation -> {
            tracked.add(invocation.<TrackingTarget>getArgument(0).deliveryId());
            return null;
        }).when(trackingService).track(org.mockito.ArgumentMatchers.any());
        return tracked;
    }

    @Test
    @DisplayName("앞쪽 배송이 상한 이상 배송 중으로 남아도 다음 실행은 멈춘 배송 ID 다음부터 이어 읽어 뒤쪽 배송을 조회한다(#656)")
    void nextRunContinuesAfterCap() {
        List<Long> tracked = givenInProgress(5);
        DeliveryStatusPoller poller = poller(2, 3, runsNow);

        poller.trackDeliveries();
        poller.trackDeliveries();

        assertThat(tracked).containsExactly(1L, 2L, 3L, 4L, 5L, 1L);
    }

    @Test
    @DisplayName("끝에 닿으면 처음으로 돌아가고, 한 바퀴를 다 돈 다음 실행은 처음부터다")
    void wrapsAround() {
        List<Long> tracked = givenInProgress(5);
        DeliveryStatusPoller poller = poller(2, 4, runsNow);

        poller.trackDeliveries();
        poller.trackDeliveries();
        poller.trackDeliveries();

        // 1회 1..4(상한) → 2회 5, 처음으로 1..3(상한, 3 기억) → 3회 4 · 5, 처음으로 1 · 2(상한)
        assertThat(tracked).containsExactly(1L, 2L, 3L, 4L, 5L, 1L, 2L, 3L, 4L, 5L, 1L, 2L);
    }

    @Test
    @DisplayName("상한에 닿은 실행은 WARN 한 줄(작업 이름 · 건수 · 상한 · 이어 읽을 위치), 닿지 않으면 INFO 한 줄")
    void warnsWhenCapped(CapturedOutput output) {
        givenInProgress(3);

        poller(2, 2, runsNow).trackDeliveries();
        poller(2, 10, runsNow).trackDeliveries();

        assertThat(output).containsPattern("WARN.*스케줄러 상한 도달: job=delivery-tracking targets=2 processed=2 failed=0 "
                + "maxPerRun=2 resumeAfterId=2")
            .containsPattern("INFO.*스케줄러 실행: job=delivery-tracking targets=3 ");
    }
}
