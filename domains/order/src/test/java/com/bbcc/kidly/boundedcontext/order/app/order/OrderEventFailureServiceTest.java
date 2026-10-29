package com.bbcc.kidly.boundedcontext.order.app.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.order.app.OrderActor;
import com.bbcc.kidly.boundedcontext.order.app.OrderErrorCode;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderEventFailureResult;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.StockDeductionFailedCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderEventFailureRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import com.bbcc.kidly.boundedcontext.order.domain.recovery.OrderEventFailure;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class OrderEventFailureServiceTest {

    private static final Long FAILURE_ID = 3L;
    private static final Long ADMIN_ID = 9_000L;
    private static final Long ORDER_ID = 12L;
    private static final Order.Policy POLICY = new Order.Policy(Duration.ofMinutes(10), Duration.ofDays(15));
    private static final OrderActor ADMIN = OrderActor.admin(ADMIN_ID, OrderActor.EVENT_FAILURE_RETRY);

    @Mock
    private OrderEventFailureRepository failureRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderService orderService;

    private OrderEventFailureService service;

    @BeforeEach
    void setUp() {
        service = new OrderEventFailureService(failureRepository, orderRepository, orderService);
    }

    @Nested
    @DisplayName("실패 보관")
    class RecordFailure {

        @Test
        @DisplayName("기록이 없으면 종류 · 주문 · 재고 부족 사유 · 실패 코드로 PENDING · 시도 1을 새로 저장한다(주문은 읽지 않음)")
        void opens() {
            StockDeductionFailedCommand command = new StockDeductionFailedCommand(ORDER_ID, List.of(
                new StockDeductionFailedCommand.Item(41L, "OUT_OF_STOCK")));
            when(failureRepository.findByOrderIdAndEventTypeForUpdateNoWait(ORDER_ID,
                OrderEventFailure.EventType.STOCK_DEDUCTION_FAILED)).thenReturn(Optional.empty());
            when(failureRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            OrderEventFailureResult result = service.recordStockDeductionFailedFailure(command,
                new DataIntegrityViolationException("x"));

            assertThat(result.eventType()).isEqualTo("STOCK_DEDUCTION_FAILED");
            assertThat(result.orderId()).isEqualTo(ORDER_ID);
            assertThat(result.stockFailures())
                .containsExactly(new OrderEventFailureResult.StockFailure(41L, "OUT_OF_STOCK"));
            assertThat(result.status()).isEqualTo("PENDING");
            assertThat(result.attemptCount()).isEqualTo(1);
            assertThat(result.failureCode()).isEqualTo("DataIntegrityViolationException");
            verifyNoInteractions(orderRepository, orderService);
        }

        @Test
        @DisplayName("같은 (주문, 종류) 기록이 있으면 새로 만들지 않고 시도 +1 · 실패 코드 갱신")
        void countsExisting() {
            OrderEventFailure existing = pending(OrderEventFailure.EventType.PAYMENT_FAILED);
            when(failureRepository.findByOrderIdAndEventTypeForUpdateNoWait(ORDER_ID,
                OrderEventFailure.EventType.PAYMENT_FAILED)).thenReturn(Optional.of(existing));

            OrderEventFailureResult result = service.recordPaymentFailedFailure(ORDER_ID,
                new IllegalStateException("x"));

            assertThat(result.attemptCount()).isEqualTo(2);
            assertThat(result.failureCode()).isEqualTo("IllegalStateException");
            verify(failureRepository, never()).save(any());
        }

        @Test
        @DisplayName("재고 차감됨 실패는 STOCK_DEDUCTED 종류로 보관한다")
        void stockDeducted() {
            when(failureRepository.findByOrderIdAndEventTypeForUpdateNoWait(ORDER_ID,
                OrderEventFailure.EventType.STOCK_DEDUCTED)).thenReturn(Optional.empty());
            when(failureRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            assertThat(service.recordStockDeductedFailure(ORDER_ID, new IllegalStateException("x")).eventType())
                .isEqualTo("STOCK_DEDUCTED");
        }
    }

    @Nested
    @DisplayName("재처리")
    class Recover {

        @Test
        @DisplayName("결제대기 주문이면 재고 차감됨은 결제 요청을 다시 맡기고 RECOVERED")
        void stockDeducted() {
            OrderEventFailure failure = lockedPending(OrderEventFailure.EventType.STOCK_DEDUCTED);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(placedOrder()));

            OrderEventFailureResult result = service.recover(FAILURE_ID, ADMIN_ID);

            assertThat(result.status()).isEqualTo("RECOVERED");
            assertThat(failure.getAttemptCount()).isEqualTo(1);
            verify(orderService).requestPayment(ORDER_ID);
        }

        @Test
        @DisplayName("재고 차감 실패는 보관한 주문상품별 사유로 관리자 주체의 재고 부족 취소를 맡긴다")
        void stockDeductionFailed() {
            lockedPending(OrderEventFailure.EventType.STOCK_DEDUCTION_FAILED);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(placedOrder()));

            service.recover(FAILURE_ID, ADMIN_ID);

            verify(orderService).cancelForStockDeductionFailure(new StockDeductionFailedCommand(ORDER_ID, List.of(
                new StockDeductionFailedCommand.Item(41L, "OUT_OF_STOCK"))), ADMIN);
        }

        @Test
        @DisplayName("결제 실패는 관리자 주체의 결제 실패 취소를 맡긴다")
        void paymentFailed() {
            lockedPending(OrderEventFailure.EventType.PAYMENT_FAILED);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(placedOrder()));

            service.recover(FAILURE_ID, ADMIN_ID);

            verify(orderService).cancelForPaymentFailure(ORDER_ID, ADMIN);
        }

        @Test
        @DisplayName("주문이 이미 끝났으면(만료 취소) 처리하지 않고 기록만 CANCELLED, 주문 상태는 그대로")
        void orderAlreadyClosed() {
            lockedPending(OrderEventFailure.EventType.STOCK_DEDUCTION_FAILED);
            Order order = placedOrder();
            order.cancel(Order.CancelReason.PAYMENT_EXPIRED);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            OrderEventFailureResult result = service.recover(FAILURE_ID, ADMIN_ID);

            assertThat(result.status()).isEqualTo("CANCELLED");
            assertThat(order.getCancelReason()).isEqualTo(Order.CancelReason.PAYMENT_EXPIRED);
            verifyNoInteractions(orderService);
        }

        @Test
        @DisplayName("이미 복구된 기록이면 다시 처리하지 않고 그대로 돌려준다")
        void alreadyRecovered() {
            OrderEventFailure failure = pending(OrderEventFailure.EventType.STOCK_DEDUCTED);
            failure.recover(LocalDateTime.now());
            when(failureRepository.findByIdForUpdate(FAILURE_ID)).thenReturn(Optional.of(failure));

            assertThat(service.recover(FAILURE_ID, ADMIN_ID).status()).isEqualTo("RECOVERED");
            verifyNoInteractions(orderRepository, orderService);
        }

        @Test
        @DisplayName("종료된 기록이면 409 EVENT_FAILURE_CLOSED")
        void closed() {
            OrderEventFailure failure = pending(OrderEventFailure.EventType.STOCK_DEDUCTED);
            failure.cancel(LocalDateTime.now());
            when(failureRepository.findByIdForUpdate(FAILURE_ID)).thenReturn(Optional.of(failure));

            assertThatThrownBy(() -> service.recover(FAILURE_ID, ADMIN_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                    error -> assertThat(error.getErrorCode()).isEqualTo(OrderErrorCode.EVENT_FAILURE_CLOSED));
            verifyNoInteractions(orderService);
        }

        @Test
        @DisplayName("없는 기록이면 404 EVENT_FAILURE_NOT_FOUND")
        void notFound() {
            when(failureRepository.findByIdForUpdate(FAILURE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.recover(FAILURE_ID, ADMIN_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                    error -> assertThat(error.getErrorCode()).isEqualTo(OrderErrorCode.EVENT_FAILURE_NOT_FOUND));
        }

        @Test
        @DisplayName("재처리가 다시 실패하면 시도 +1 · 실패 코드 갱신, 그 사이 닫힌 기록은 그대로")
        void retryFailure() {
            OrderEventFailure open = pending(OrderEventFailure.EventType.STOCK_DEDUCTED);
            when(failureRepository.findById(FAILURE_ID)).thenReturn(Optional.of(open));

            OrderEventFailureResult result = service.recordRetryFailure(FAILURE_ID, new IllegalStateException("x"));

            assertThat(result.attemptCount()).isEqualTo(2);
            assertThat(result.status()).isEqualTo("PENDING");

            open.recover(LocalDateTime.now());
            assertThat(service.recordRetryFailure(FAILURE_ID, new IllegalStateException("y")).attemptCount())
                .isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("종료")
    class Cancel {

        @Test
        @DisplayName("PENDING이면 기록만 CANCELLED, 주문은 읽지도 바꾸지도 않는다. 다시 눌러도 그대로 200")
        void cancels() {
            lockedPending(OrderEventFailure.EventType.STOCK_DEDUCTED);

            assertThat(service.cancel(FAILURE_ID).status()).isEqualTo("CANCELLED");
            assertThat(service.cancel(FAILURE_ID).status()).isEqualTo("CANCELLED");
            verifyNoInteractions(orderRepository, orderService);
        }

        @Test
        @DisplayName("이미 복구된 기록이면 409 EVENT_FAILURE_CLOSED")
        void recovered() {
            OrderEventFailure failure = pending(OrderEventFailure.EventType.STOCK_DEDUCTED);
            failure.recover(LocalDateTime.now());
            when(failureRepository.findByIdForUpdate(FAILURE_ID)).thenReturn(Optional.of(failure));

            assertThatThrownBy(() -> service.cancel(FAILURE_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                    error -> assertThat(error.getErrorCode()).isEqualTo(OrderErrorCode.EVENT_FAILURE_CLOSED));
        }
    }

    @Nested
    @DisplayName("목록")
    class GetFailures {

        @Test
        @DisplayName("상태를 주면 그 상태만 최근 기록부터")
        void byStatus() {
            when(failureRepository.findAllByStatusOrderByIdDesc(OrderEventFailure.Status.PENDING,
                PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(pending(OrderEventFailure.EventType.STOCK_DEDUCTED))));

            assertThat(service.getFailures("PENDING", 0, 20).getContent()).singleElement()
                .extracting(OrderEventFailureResult::eventType).isEqualTo("STOCK_DEDUCTED");
        }

        @Test
        @DisplayName("상태가 없으면 모든 기록")
        void all() {
            ArgumentCaptor<PageRequest> pageable = ArgumentCaptor.forClass(PageRequest.class);
            when(failureRepository.findAllByOrderByIdDesc(pageable.capture())).thenReturn(new PageImpl<>(List.of()));

            assertThat(service.getFailures(null, 1, 5).getContent()).isEmpty();
            assertThat(pageable.getValue()).isEqualTo(PageRequest.of(1, 5));
        }

        @ParameterizedTest
        @ValueSource(strings = {"pending", "NOT_RETRYABLE", "UNKNOWN"})
        @DisplayName("모르는 상태(소문자 · 결제 복구 전용 값 포함)는 400 INVALID_EVENT_FAILURE_STATUS")
        void invalidStatus(String status) {
            assertThatThrownBy(() -> service.getFailures(status, 0, 20))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getErrorCode())
                    .isEqualTo(OrderErrorCode.INVALID_EVENT_FAILURE_STATUS));
        }
    }

    private OrderEventFailure lockedPending(OrderEventFailure.EventType eventType) {
        OrderEventFailure failure = pending(eventType);
        when(failureRepository.findByIdForUpdate(FAILURE_ID)).thenReturn(Optional.of(failure));
        return failure;
    }

    private static OrderEventFailure pending(OrderEventFailure.EventType eventType) {
        boolean stock = eventType == OrderEventFailure.EventType.STOCK_DEDUCTION_FAILED;
        List<OrderEventFailure.StockFailure> stockFailures = stock
            ? List.of(new OrderEventFailure.StockFailure(41L, "OUT_OF_STOCK"))
            : List.of();
        return OrderEventFailure.open(eventType, ORDER_ID, stockFailures, "DataIntegrityViolationException",
            LocalDateTime.now());
    }

    private static Order placedOrder() {
        return Order.place(1L, List.of(new Order.RequestedItem(new OrderItem.ProductSnapshot(
                1L, 1L, 2L, "상품 A", null, "110", 25_000L, 1L), true, null, 1, 25_000L)),
            new Order.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1"), POLICY,
            LocalDateTime.now());
    }
}
