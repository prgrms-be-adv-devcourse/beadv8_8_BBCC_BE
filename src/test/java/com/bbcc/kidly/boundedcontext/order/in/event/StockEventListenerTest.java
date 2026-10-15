package com.bbcc.kidly.boundedcontext.order.in.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.StockDeductionFailedCommand;
import com.bbcc.kidly.shared.product.domain.StockDeductionFailureCode;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductionFailedEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class StockEventListenerTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 14, 9, 0);

    @Mock
    private OrderService orderService;

    @InjectMocks
    private StockEventListener listener;

    @Test
    @DisplayName("재고 차감 이벤트의 주문 ID로 결제 요청을 맡긴다")
    void requestsPayment() {
        listener.onStockDeducted(new StockDeductedEvent(UUID.randomUUID(), OCCURRED_AT,
            new StockDeductedEvent.Data(7L)));

        verify(orderService).requestPayment(7L);
    }

    @Test
    @DisplayName("주문 ID가 없으면 WARN을 남기고 결제 요청을 맡기지 않는다")
    void skipsWithoutOrderId(CapturedOutput output) {
        listener.onStockDeducted(new StockDeductedEvent(UUID.randomUUID(), OCCURRED_AT,
            new StockDeductedEvent.Data(null)));

        verifyNoInteractions(orderService);
        assertThat(output).containsPattern("WARN.*event=stock.deducted.*reason=missing-order-id");
    }

    @Test
    @DisplayName("데이터가 없으면 WARN을 남기고 결제 요청을 맡기지 않는다")
    void skipsWithoutData(CapturedOutput output) {
        listener.onStockDeducted(new StockDeductedEvent(UUID.randomUUID(), OCCURRED_AT, null));

        verifyNoInteractions(orderService);
        assertThat(output).containsPattern("WARN.*event=stock.deducted.*reason=missing-order-id");
    }

    @Nested
    @DisplayName("재고 차감 실패 이벤트")
    class OnStockDeductionFailed {

        @Test
        @DisplayName("주문 ID와 주문상품별 사유(enum 이름)로 재고 부족 취소를 맡기고, 리스너는 수신 로그를 남기지 않는다(결과는 서비스가 한 줄)")
        void convertsEvent(CapturedOutput output) {
            listener.onStockDeductionFailed(failed(7L, StockDeductionFailureCode.OUT_OF_STOCK, List.of(
                new StockDeductionFailedEvent.Failure(70L, StockDeductionFailureCode.OUT_OF_STOCK),
                new StockDeductionFailedEvent.Failure(71L, StockDeductionFailureCode.OPTION_NOT_ON_SALE))));

            verify(orderService).cancelForStockDeductionFailure(new StockDeductionFailedCommand(7L, List.of(
                new StockDeductionFailedCommand.Item(70L, "OUT_OF_STOCK"),
                new StockDeductionFailedCommand.Item(71L, "OPTION_NOT_ON_SALE"))));
            assertThat(output).doesNotContain("재고 차감 실패 수신").doesNotContain("event=stock.deduction-failed");
        }

        @Test
        @DisplayName("주문 ID가 없으면 WARN을 남기고 취소를 맡기지 않는다")
        void skipsWithoutOrderId(CapturedOutput output) {
            listener.onStockDeductionFailed(failed(null, StockDeductionFailureCode.OUT_OF_STOCK, List.of()));

            verifyNoInteractions(orderService);
            assertThat(output).containsPattern("WARN.*event=stock.deduction-failed.*reason=missing-order-id");
        }

        @Test
        @DisplayName("데이터가 없으면 WARN을 남기고 취소를 맡기지 않는다")
        void skipsWithoutData(CapturedOutput output) {
            listener.onStockDeductionFailed(new StockDeductionFailedEvent(UUID.randomUUID(), OCCURRED_AT, null));

            verifyNoInteractions(orderService);
            assertThat(output).containsPattern("WARN.*event=stock.deduction-failed.*reason=missing-order-id");
        }

        @Test
        @DisplayName("주문상품 목록이 없으면(null) 빈 목록으로 취소를 맡긴다(주문은 취소, 주문상품 사유 없음)")
        void nullFailuresAsEmpty() {
            listener.onStockDeductionFailed(failed(7L, StockDeductionFailureCode.OUT_OF_STOCK, null));

            verify(orderService).cancelForStockDeductionFailure(new StockDeductionFailedCommand(7L, List.of()));
        }

        @Test
        @DisplayName("빈 줄 · 주문상품 ID 없는 줄은 그 줄만 건너뛰고 WARN(reason=missing-order-item-id), 나머지는 넘긴다")
        void skipsLineWithoutOrderItemId(CapturedOutput output) {
            List<StockDeductionFailedEvent.Failure> failures = new ArrayList<>();
            failures.add(null);
            failures.add(new StockDeductionFailedEvent.Failure(null, StockDeductionFailureCode.OUT_OF_STOCK));
            failures.add(new StockDeductionFailedEvent.Failure(71L, StockDeductionFailureCode.OUT_OF_STOCK));

            listener.onStockDeductionFailed(failed(7L, StockDeductionFailureCode.OUT_OF_STOCK, failures));

            verify(orderService).cancelForStockDeductionFailure(new StockDeductionFailedCommand(7L, List.of(
                new StockDeductionFailedCommand.Item(71L, "OUT_OF_STOCK"))));
            assertThat(warnLines(output, "reason=missing-order-item-id orderId=7")).isEqualTo(2);
        }

        @Test
        @DisplayName("사유가 없는 줄은 건너뛰고 WARN(reason=missing-failure-reason), 주문 취소는 그대로 맡긴다")
        void skipsLineWithoutReason(CapturedOutput output) {
            listener.onStockDeductionFailed(failed(7L, StockDeductionFailureCode.OUT_OF_STOCK, List.of(
                new StockDeductionFailedEvent.Failure(70L, null),
                new StockDeductionFailedEvent.Failure(71L, StockDeductionFailureCode.OUT_OF_STOCK))));

            verify(orderService).cancelForStockDeductionFailure(new StockDeductionFailedCommand(7L, List.of(
                new StockDeductionFailedCommand.Item(71L, "OUT_OF_STOCK"))));
            assertThat(output).containsPattern(
                "WARN.*event=stock.deduction-failed reason=missing-failure-reason orderId=7 orderItemId=70");
        }

        @Test
        @DisplayName("같은 주문상품 ID가 두 번 오면 마지막 사유를 쓰고 WARN(reason=duplicate-order-item-id)")
        void duplicateOrderItemIdUsesLast(CapturedOutput output) {
            listener.onStockDeductionFailed(failed(7L, StockDeductionFailureCode.OUT_OF_STOCK, List.of(
                new StockDeductionFailedEvent.Failure(70L, StockDeductionFailureCode.OUT_OF_STOCK),
                new StockDeductionFailedEvent.Failure(71L, StockDeductionFailureCode.OUT_OF_STOCK),
                new StockDeductionFailedEvent.Failure(70L, StockDeductionFailureCode.PRODUCT_NOT_ON_SALE))));

            verify(orderService).cancelForStockDeductionFailure(new StockDeductionFailedCommand(7L, List.of(
                new StockDeductionFailedCommand.Item(70L, "PRODUCT_NOT_ON_SALE"),
                new StockDeductionFailedCommand.Item(71L, "OUT_OF_STOCK"))));
            assertThat(output).containsPattern(
                "WARN.*event=stock.deduction-failed reason=duplicate-order-item-id orderId=7 orderItemId=70");
        }

        private StockDeductionFailedEvent failed(Long orderId, StockDeductionFailureCode reason,
            List<StockDeductionFailedEvent.Failure> failures) {
            return new StockDeductionFailedEvent(UUID.randomUUID(), OCCURRED_AT,
                new StockDeductionFailedEvent.Data(orderId, reason, failures));
        }

        private long warnLines(CapturedOutput output, String text) {
            return output.getAll().lines()
                .filter(line -> line.contains("WARN") && line.contains("event=stock.deduction-failed " + text))
                .count();
        }
    }
}
