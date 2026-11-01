package com.bbcc.kidly.boundedcontext.delivery.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 택배 조회로 배송 완료 · 조회 중지 규칙. 택배사 시각이 발송 시각보다 뒤일 때만 완료한다(엉터리 · 재사용 운송장 방지).
 */
class DeliveryTrackingTest {

    private static final LocalDateTime SHIPPED_AT = LocalDateTime.of(2026, 11, 1, 12, 0);

    private static Delivery shipped() {
        Delivery delivery = Delivery.create(300L, 10L);
        delivery.ship(Delivery.Carrier.DUMMY, "2026-11-01T06:00:00Z", SHIPPED_AT);
        return delivery;
    }

    @ParameterizedTest(name = "발송 시각 {0}초 뒤 택배사 완료 → 완료 {1}")
    @CsvSource({"-1, false", "0, false", "1, true"})
    @DisplayName("택배사 완료 시각이 발송 시각보다 뒤일 때만(같으면 아님) 배송 완료하고, 완료 시각은 택배사 시각이다")
    void completesOnlyAfterShippedAt(long secondsAfterShipped, boolean expected) {
        Delivery delivery = shipped();
        LocalDateTime trackedAt = SHIPPED_AT.plusSeconds(secondsAfterShipped);

        boolean completed = delivery.completeByTracking(trackedAt);

        assertThat(completed).isEqualTo(expected);
        if (expected) {
            assertThat(delivery.getStatus()).isEqualTo(Delivery.Status.DELIVERED);
            assertThat(delivery.getDeliveredAt()).isEqualTo(trackedAt);
        } else {
            assertThat(delivery.getStatus()).isEqualTo(Delivery.Status.SHIPPING);
            assertThat(delivery.getDeliveredAt()).isNull();
        }
    }

    @Test
    @DisplayName("이미 배송 완료면(택배 조회든 수동이든) 두 번째 완료는 false이고 처음 완료 시각을 유지한다")
    void secondCompletionKeepsFirst() {
        Delivery byTracking = shipped();
        byTracking.completeByTracking(SHIPPED_AT.plusHours(1));
        Delivery byManual = shipped();
        byManual.complete(SHIPPED_AT.plusHours(2));

        assertThat(byTracking.completeByTracking(SHIPPED_AT.plusHours(3))).isFalse();
        assertThat(byTracking.getDeliveredAt()).isEqualTo(SHIPPED_AT.plusHours(1));
        assertThat(byManual.completeByTracking(SHIPPED_AT.plusHours(3))).isFalse();
        assertThat(byManual.getDeliveredAt()).isEqualTo(SHIPPED_AT.plusHours(2));
    }

    @Test
    @DisplayName("택배 조회로 완료한 뒤 판매자가 수동 완료해도 예외 없이 처음 시각을 유지한다")
    void manualAfterTrackingKeepsFirst() {
        Delivery delivery = shipped();
        delivery.completeByTracking(SHIPPED_AT.plusHours(1));

        delivery.complete(SHIPPED_AT.plusHours(5));

        assertThat(delivery.getDeliveredAt()).isEqualTo(SHIPPED_AT.plusHours(1));
    }

    @Test
    @DisplayName("발송 전 · 취소된 배송은 택배 완료가 와도 예외 없이 false다")
    void notShippedIsNotCompleted() {
        Delivery ready = Delivery.create(300L, 10L);
        Delivery cancelled = Delivery.create(301L, 10L);
        cancelled.cancel();

        assertThat(ready.completeByTracking(SHIPPED_AT.plusHours(1))).isFalse();
        assertThat(cancelled.completeByTracking(SHIPPED_AT.plusHours(1))).isFalse();
        assertThat(ready.getStatus()).isEqualTo(Delivery.Status.READY);
        assertThat(cancelled.getStatus()).isEqualTo(Delivery.Status.CANCELLED);
    }

    @Test
    @DisplayName("조회 중지는 발송 중일 때 한 번만 기록되고, 중지된 배송은 택배 완료가 와도 바뀌지 않는다")
    void stopTrackingOnceAndBlocksTrackingCompletion() {
        Delivery delivery = shipped();

        assertThat(delivery.stopTracking(SHIPPED_AT.plusMinutes(1))).isTrue();
        assertThat(delivery.stopTracking(SHIPPED_AT.plusMinutes(2))).isFalse();
        assertThat(delivery.getTrackingStoppedAt()).isEqualTo(SHIPPED_AT.plusMinutes(1));
        assertThat(delivery.completeByTracking(SHIPPED_AT.plusHours(1))).isFalse();
        assertThat(delivery.getStatus()).isEqualTo(Delivery.Status.SHIPPING);
    }

    @Test
    @DisplayName("조회 중지된 배송도 판매자 수동 완료는 된다")
    void manualCompletionAfterStop() {
        Delivery delivery = shipped();
        delivery.stopTracking(SHIPPED_AT.plusMinutes(1));

        delivery.complete(SHIPPED_AT.plusHours(1));

        assertThat(delivery.getStatus()).isEqualTo(Delivery.Status.DELIVERED);
    }

    @Test
    @DisplayName("발송 전 · 배송 완료된 배송은 조회 중지를 기록하지 않는다")
    void stopTrackingOnlyWhenShipping() {
        Delivery ready = Delivery.create(300L, 10L);
        Delivery delivered = shipped();
        delivered.complete(SHIPPED_AT.plusHours(1));

        assertThat(ready.stopTracking(SHIPPED_AT)).isFalse();
        assertThat(delivered.stopTracking(SHIPPED_AT.plusHours(2))).isFalse();
        assertThat(delivered.getTrackingStoppedAt()).isNull();
    }
}
