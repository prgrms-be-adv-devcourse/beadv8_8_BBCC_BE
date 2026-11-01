package com.bbcc.kidly.boundedcontext.delivery.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.delivery.app.dto.TrackingTarget;
import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryStatusChecker;
import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryStatusChecker.TrackingResult;
import com.bbcc.kidly.boundedcontext.delivery.domain.Delivery;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

/**
 * 택배 조회 결과 4종이 각각 무엇을 부르는지 본다. 판단(완료해도 되는지)은 Delivery · DeliveryService가 한다.
 */
@ExtendWith(MockitoExtension.class)
class DeliveryTrackingServiceTest {

    private static final TrackingTarget TARGET = new TrackingTarget(7L, Delivery.Carrier.DUMMY, "2026-11-01T09:00:00Z");

    @Mock
    private DeliveryStatusChecker checker;

    @Mock
    private DeliveryService deliveryService;

    @InjectMocks
    private DeliveryTrackingService trackingService;

    @Test
    @DisplayName("DELIVERED면 택배사 시각으로 배송 완료를 맡긴다")
    void delivered() {
        LocalDateTime trackedAt = LocalDateTime.of(2026, 11, 1, 18, 0);
        given(checker.check(Delivery.Carrier.DUMMY, TARGET.trackingNumber()))
            .willReturn(TrackingResult.delivered(trackedAt));

        trackingService.track(TARGET);

        verify(deliveryService).completeByTracking(7L, trackedAt);
        verify(deliveryService, never()).stopTracking(anyLong(), anyString());
    }

    @Test
    @DisplayName("번호 형식 오류면 조회를 멈춘다")
    void invalidNumberStops() {
        given(checker.check(any(), any())).willReturn(TrackingResult.invalidTrackingNumber("BAD_REQUEST"));

        trackingService.track(TARGET);

        verify(deliveryService).stopTracking(7L, "BAD_REQUEST");
        verify(deliveryService, never()).completeByTracking(anyLong(), any());
    }

    @Test
    @DisplayName("배송 중 · 일시 오류면 아무것도 바꾸지 않는다(다음 주기에 다시 조회)")
    void inProgressAndUnavailableChangeNothing() {
        given(checker.check(any(), any()))
            .willReturn(TrackingResult.inProgress(), TrackingResult.unavailable("NETWORK"));

        trackingService.track(TARGET);
        trackingService.track(TARGET);

        verify(deliveryService, never()).completeByTracking(anyLong(), any());
        verify(deliveryService, never()).stopTracking(anyLong(), anyString());
        // 일시 오류도 그 자리에서 다시 부르지 않는다(한 번 조회 = 한 번 호출, 재시도는 다음 주기)
        verify(checker, times(2)).check(any(), any());
    }

    @Test
    @DisplayName("외부 호출(최대 수 초)은 DB 트랜잭션 밖이다: 조회 서비스에는 트랜잭션을 걸지 않는다(상태 변경만 DeliveryService의 짧은 트랜잭션)")
    void noTransactionAroundExternalCall() {
        assertThat(DeliveryTrackingService.class.isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(DeliveryTrackingService.class.getDeclaredMethods())
            .noneMatch(method -> method.isAnnotationPresent(Transactional.class));
    }
}
