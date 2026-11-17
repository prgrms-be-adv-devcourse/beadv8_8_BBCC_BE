package com.bbcc.kidly.boundedcontext.delivery.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.bbcc.kidly.boundedcontext.delivery.app.dto.DeliveryTrackingView;
import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryOrders;
import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryRepository;
import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryStatusChecker;
import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryStatusChecker.TrackingEvent;
import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryStatusChecker.TrackingHistory;
import com.bbcc.kidly.boundedcontext.delivery.domain.Delivery;
import com.bbcc.kidly.boundedcontext.order.CapturedLogs;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 구매자 택배 진행 이력 서비스(#1320): 구매자 확인(404) · 발송 전 · DUMMY(200 빈 이력) · 조회 결과별 응답(409 · 503) · 10분 캐시(성공만).
 */
class TrackingHistoryServiceTest {

    private static final Long BUYER_ID = 1L;
    private static final Long ORDER_ID = 7L;
    private static final Long DELIVERY_ID = 5L;
    private static final Long SELLER_ID = 10L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 9, 0);
    private static final List<TrackingEvent> EVENTS =
        List.of(new TrackingEvent(LocalDateTime.of(2026, 11, 12, 8, 0), "대전HUB", "IN_TRANSIT"));

    private final DeliveryRepository deliveryRepository = mock(DeliveryRepository.class);
    private final DeliveryOrders deliveryOrders = mock(DeliveryOrders.class);
    private final DeliveryStatusChecker checker = mock(DeliveryStatusChecker.class);
    private final MutableClock clock = new MutableClock();
    private final TrackingHistoryService service =
        new TrackingHistoryService(deliveryRepository, deliveryOrders, checker, clock);
    private Delivery delivery;

    @BeforeEach
    void setUp() {
        delivery = Delivery.create(ORDER_ID, SELLER_ID);
        ReflectionTestUtils.setField(delivery, "id", DELIVERY_ID);
        delivery.ship(Delivery.Carrier.HANJIN, "123456789012", NOW);
        when(deliveryRepository.findById(DELIVERY_ID)).thenReturn(Optional.of(delivery));
        when(deliveryOrders.findOrder(ORDER_ID)).thenReturn(Optional.of(
            new DeliveryOrders.Order(ORDER_ID, BUYER_ID, NOW, null, List.of())));
        when(checker.history(Delivery.Carrier.HANJIN, "123456789012")).thenReturn(TrackingHistory.ok(EVENTS));
    }

    @Test
    @DisplayName("그 주문 구매자면 택배사 · 운송장 · 이벤트(최신 먼저, 포트가 준 순서 그대로)를 돌려준다")
    void returnsHistory() {
        assertThat(service.getTracking(BUYER_ID, ORDER_ID, DELIVERY_ID)).isEqualTo(
            new DeliveryTrackingView(DELIVERY_ID, "HANJIN", "123456789012", EVENTS));
    }

    @Test
    @DisplayName("없는 배송 · 다른 주문의 배송 · 남의 주문 · 주문 없음은 모두 404이고 택배 조회를 부르지 않는다")
    void notFoundCases() {
        when(deliveryOrders.findOrder(8L)).thenReturn(Optional.of(
            new DeliveryOrders.Order(8L, BUYER_ID, NOW, null, List.of())));

        assertCode(() -> service.getTracking(BUYER_ID, ORDER_ID, 99L), DeliveryErrorCode.TRACKING_DELIVERY_NOT_FOUND);
        assertCode(() -> service.getTracking(BUYER_ID, 8L, DELIVERY_ID), DeliveryErrorCode.TRACKING_DELIVERY_NOT_FOUND);
        assertCode(() -> service.getTracking(2L, ORDER_ID, DELIVERY_ID), DeliveryErrorCode.TRACKING_DELIVERY_NOT_FOUND);
        when(deliveryOrders.findOrder(ORDER_ID)).thenReturn(Optional.empty());
        assertCode(() -> service.getTracking(BUYER_ID, ORDER_ID, DELIVERY_ID),
            DeliveryErrorCode.TRACKING_DELIVERY_NOT_FOUND);
        verify(checker, never()).history(any(), any());
    }

    @Test
    @DisplayName("운송장이 없으면(발송 전) 200 빈 이력 · 택배사 · 운송장 null이고 택배 조회를 부르지 않는다")
    void notShippedIsEmpty() {
        Delivery ready = Delivery.create(ORDER_ID, SELLER_ID);
        when(deliveryRepository.findById(6L)).thenReturn(Optional.of(ready));

        assertThat(service.getTracking(BUYER_ID, ORDER_ID, 6L))
            .isEqualTo(new DeliveryTrackingView(6L, null, null, List.of()));
        verify(checker, never()).history(any(), any());
    }

    @Test
    @DisplayName("조회할 수 없는 택배사(DUMMY 등)는 200 빈 이력(택배사 · 운송장은 그대로)이고 캐시하지 않는다")
    void unsupportedCarrierIsEmpty() {
        when(checker.history(any(), any())).thenReturn(TrackingHistory.unsupportedCarrier());

        assertThat(service.getTracking(BUYER_ID, ORDER_ID, DELIVERY_ID))
            .isEqualTo(new DeliveryTrackingView(DELIVERY_ID, "HANJIN", "123456789012", List.of()));
        assertThat(service.cacheSize()).isZero();
    }

    @Test
    @DisplayName("조회 결과별: 운송장 형식 오류 409, 일시 오류(서킷 열림 · 꺼짐) 503")
    void resultKinds() {
        when(checker.history(any(), any()))
            .thenReturn(TrackingHistory.invalidTrackingNumber("BAD_REQUEST"))
            .thenReturn(TrackingHistory.unavailable("CIRCUIT_OPEN"))
            .thenReturn(TrackingHistory.unavailable("DISABLED"));

        assertCode(() -> service.getTracking(BUYER_ID, ORDER_ID, DELIVERY_ID),
            DeliveryErrorCode.TRACKING_INVALID_NUMBER);
        assertCode(() -> service.getTracking(BUYER_ID, ORDER_ID, DELIVERY_ID), DeliveryErrorCode.TRACKING_UNAVAILABLE);
        assertCode(() -> service.getTracking(BUYER_ID, ORDER_ID, DELIVERY_ID), DeliveryErrorCode.TRACKING_UNAVAILABLE);
        verify(checker, times(3)).history(any(), any());
    }

    @Test
    @DisplayName("조회 결과는 10분 동안 캐시해 다시 부르지 않고, 10분이 지나면 다시 부른다")
    void cachesSuccessForTenMinutes() {
        service.getTracking(BUYER_ID, ORDER_ID, DELIVERY_ID);
        clock.advance(Duration.ofMinutes(10).minusSeconds(1));
        service.getTracking(BUYER_ID, ORDER_ID, DELIVERY_ID);
        verify(checker, times(1)).history(any(), any());

        clock.advance(Duration.ofSeconds(1));
        service.getTracking(BUYER_ID, ORDER_ID, DELIVERY_ID);
        verify(checker, times(2)).history(any(), any());
    }

    @Test
    @DisplayName("택배사에 아직 등록 전(빈 이력)은 1분만 캐시하고, 1분이 지나면 다시 부른다")
    void notRegisteredCachedForOneMinute() {
        when(checker.history(any(), any())).thenReturn(TrackingHistory.notRegistered());

        assertThat(service.getTracking(BUYER_ID, ORDER_ID, DELIVERY_ID).events()).isEmpty();
        clock.advance(Duration.ofMinutes(1).minusSeconds(1));
        service.getTracking(BUYER_ID, ORDER_ID, DELIVERY_ID);
        verify(checker, times(1)).history(any(), any());

        clock.advance(Duration.ofSeconds(1));
        service.getTracking(BUYER_ID, ORDER_ID, DELIVERY_ID);
        verify(checker, times(2)).history(any(), any());
    }

    @Test
    @DisplayName("서킷 열림 · 꺼짐은 요청마다 반복되므로 WARN을 남기지 않고(DEBUG), 그 밖의 일시 오류는 WARN 한 줄(이유 코드만)")
    void quietReasonsAreNotWarned() {
        when(checker.history(any(), any()))
            .thenReturn(TrackingHistory.unavailable("CIRCUIT_OPEN"))
            .thenReturn(TrackingHistory.unavailable("DISABLED"))
            .thenReturn(TrackingHistory.unavailable("NETWORK"));

        try (CapturedLogs logs = CapturedLogs.start()) {
            for (int i = 0; i < 3; i++) {
                assertCode(() -> service.getTracking(BUYER_ID, ORDER_ID, DELIVERY_ID),
                    DeliveryErrorCode.TRACKING_UNAVAILABLE);
            }
            assertThat(logs.events()).filteredOn(event -> event.getLevel() == Level.WARN)
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("택배 진행 이력 조회 실패: deliveryId=5 reason=NETWORK");
        }
    }

    @Test
    @DisplayName("캐시된 결과도 남의 주문이면 404다(구매자 확인을 캐시보다 먼저 한다)")
    void cacheDoesNotSkipOwnership() {
        service.getTracking(BUYER_ID, ORDER_ID, DELIVERY_ID);

        assertCode(() -> service.getTracking(2L, ORDER_ID, DELIVERY_ID), DeliveryErrorCode.TRACKING_DELIVERY_NOT_FOUND);
    }

    @Test
    @DisplayName("실패는 캐시하지 않는다: 일시 오류 뒤 다시 보면 다시 부르고 성공을 돌려준다")
    void failureIsNotCached() {
        when(checker.history(any(), any()))
            .thenReturn(TrackingHistory.unavailable("NETWORK"))
            .thenReturn(TrackingHistory.ok(EVENTS));

        assertCode(() -> service.getTracking(BUYER_ID, ORDER_ID, DELIVERY_ID), DeliveryErrorCode.TRACKING_UNAVAILABLE);
        assertThat(service.getTracking(BUYER_ID, ORDER_ID, DELIVERY_ID).events()).isEqualTo(EVENTS);
        verify(checker, times(2)).history(any(), any());
    }

    @Test
    @DisplayName("운송장이 바뀌면(판매자 정정 등) 캐시를 쓰지 않고 새 운송장으로 다시 부른다")
    void trackingNumberChangeBypassesCache() {
        service.getTracking(BUYER_ID, ORDER_ID, DELIVERY_ID);
        ReflectionTestUtils.setField(delivery, "trackingNumber", "999999999999");
        when(checker.history(Delivery.Carrier.HANJIN, "999999999999")).thenReturn(TrackingHistory.ok(List.of()));

        assertThat(service.getTracking(BUYER_ID, ORDER_ID, DELIVERY_ID).trackingNumber()).isEqualTo("999999999999");
        verify(checker).history(Delivery.Carrier.HANJIN, "999999999999");
    }

    @Test
    @DisplayName("캐시는 1,000건을 넘지 않는다(넘으면 만료된 것부터 지우고, 그래도 넘으면 비운다)")
    void cacheIsBounded() {
        for (long id = 1; id <= TrackingHistoryService.CACHE_MAX + 5; id++) {
            Delivery other = Delivery.create(ORDER_ID, SELLER_ID);
            ReflectionTestUtils.setField(other, "id", 1_000_000L + id);
            other.ship(Delivery.Carrier.HANJIN, "123456789012", NOW);
            when(deliveryRepository.findById(1_000_000L + id)).thenReturn(Optional.of(other));
            service.getTracking(BUYER_ID, ORDER_ID, 1_000_000L + id);
        }

        assertThat(service.cacheSize()).isLessThanOrEqualTo(TrackingHistoryService.CACHE_MAX);
    }

    private static void assertCode(ThrowingCallable call, DeliveryErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-11-12T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Seoul");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
