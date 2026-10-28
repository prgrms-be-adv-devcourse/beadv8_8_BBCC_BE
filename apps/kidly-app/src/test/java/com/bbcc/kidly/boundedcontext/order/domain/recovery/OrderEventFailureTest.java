package com.bbcc.kidly.boundedcontext.order.domain.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrderEventFailureTest {

    private static final LocalDateTime NOW = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    private static final List<OrderEventFailure.StockFailure> STOCK_FAILURES = List.of(
        new OrderEventFailure.StockFailure(41L, "OUT_OF_STOCK"),
        new OrderEventFailure.StockFailure(42L, "NOT_ON_SALE"));

    @Nested
    @DisplayName("처음 보관")
    class Open {

        @Test
        @DisplayName("PENDING · 시도 1 · 이벤트 종류 · 주문 · 받은 재고 부족 사유 · 실패 코드 · 시도 시각을 그대로 담는다")
        void opensPending() {
            OrderEventFailure failure = open();

            assertThat(failure.getStatus()).isEqualTo(OrderEventFailure.Status.PENDING);
            assertThat(failure.getEventType()).isEqualTo(OrderEventFailure.EventType.STOCK_DEDUCTION_FAILED);
            assertThat(failure.getOrderId()).isEqualTo(12L);
            assertThat(failure.getStockFailures()).containsExactlyElementsOf(STOCK_FAILURES);
            assertThat(failure.getFailureCode()).isEqualTo("DataIntegrityViolationException");
            assertThat(failure.getAttemptCount()).isEqualTo(1);
            assertThat(failure.getLastAttemptedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("재고 부족 사유가 없는 이벤트(재고 차감됨 · 결제 실패)는 빈 목록")
        void withoutStockFailures() {
            OrderEventFailure failure = OrderEventFailure.open(OrderEventFailure.EventType.STOCK_DEDUCTED, 12L,
                List.of(), "IllegalStateException", NOW);

            assertThat(failure.getStockFailures()).isEmpty();
        }
    }

    @Nested
    @DisplayName("다시 실패")
    class FailAgain {

        @Test
        @DisplayName("PENDING이면 시도 +1 · 실패 코드 · 시도 시각만 바뀌고 받은 이벤트 데이터는 처음 값 그대로")
        void countsAttempt() {
            OrderEventFailure failure = open();

            failure.recordFailure("IllegalStateException", NOW.plusMinutes(1));

            assertThat(failure.getStatus()).isEqualTo(OrderEventFailure.Status.PENDING);
            assertThat(failure.getAttemptCount()).isEqualTo(2);
            assertThat(failure.getFailureCode()).isEqualTo("IllegalStateException");
            assertThat(failure.getLastAttemptedAt()).isEqualTo(NOW.plusMinutes(1));
            assertThat(failure.getStockFailures()).containsExactlyElementsOf(STOCK_FAILURES);
        }

        @Test
        @DisplayName("종료된 기록(RECOVERED · CANCELLED)은 바꾸지 않는다")
        void closedUnchanged() {
            OrderEventFailure recovered = open();
            recovered.recover(NOW);
            OrderEventFailure cancelled = open();
            cancelled.cancel(NOW);

            recovered.recordFailure("IllegalStateException", NOW.plusMinutes(1));
            cancelled.recordFailure("IllegalStateException", NOW.plusMinutes(1));

            assertThat(recovered.getStatus()).isEqualTo(OrderEventFailure.Status.RECOVERED);
            assertThat(recovered.getAttemptCount()).isEqualTo(1);
            assertThat(cancelled.getStatus()).isEqualTo(OrderEventFailure.Status.CANCELLED);
            assertThat(cancelled.getFailureCode()).isEqualTo("DataIntegrityViolationException");
        }
    }

    @Nested
    @DisplayName("종료")
    class Close {

        @Test
        @DisplayName("PENDING을 복구하면 RECOVERED · 시도 시각 갱신, 시도 횟수는 그대로")
        void recover() {
            OrderEventFailure failure = open();

            failure.recover(NOW.plusMinutes(2));

            assertThat(failure.getStatus()).isEqualTo(OrderEventFailure.Status.RECOVERED);
            assertThat(failure.getLastAttemptedAt()).isEqualTo(NOW.plusMinutes(2));
            assertThat(failure.getAttemptCount()).isEqualTo(1);
            assertThat(failure.isClosed()).isTrue();
        }

        @Test
        @DisplayName("PENDING을 종료하면 CANCELLED")
        void cancel() {
            OrderEventFailure failure = open();

            failure.cancel(NOW.plusMinutes(2));

            assertThat(failure.getStatus()).isEqualTo(OrderEventFailure.Status.CANCELLED);
            assertThat(failure.getLastAttemptedAt()).isEqualTo(NOW.plusMinutes(2));
            assertThat(failure.isClosed()).isTrue();
        }

        @Test
        @DisplayName("종료된 기록은 다시 복구 · 종료할 수 없다(서비스가 먼저 거른다)")
        void closedCannotClose() {
            OrderEventFailure failure = open();
            failure.cancel(NOW);

            assertThatThrownBy(() -> failure.recover(NOW)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> failure.cancel(NOW)).isInstanceOf(IllegalStateException.class);
        }
    }

    private static OrderEventFailure open() {
        return OrderEventFailure.open(OrderEventFailure.EventType.STOCK_DEDUCTION_FAILED, 12L, STOCK_FAILURES,
            "DataIntegrityViolationException", NOW);
    }
}
