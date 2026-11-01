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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 택배 조회 스케줄러: 비활성이면 건너뛰고, 잠금을 잡은 서버만 돌고, 배송 ID 커서로 이어 읽고, 한 건이 실패해도 다음 건을 처리한다.
 */
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
        verify(trackingService, never()).findTargets(3L, 1);
    }
}
