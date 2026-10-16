package com.bbcc.kidly.boundedcontext.order.app.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.order.app.order.dto.MarkPaidCommand;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PaymentRecoveryResult;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.PaymentRecoveryRepository;
import com.bbcc.kidly.boundedcontext.order.domain.OrderErrorCode;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import com.bbcc.kidly.boundedcontext.order.domain.order.PaymentMismatchException;
import com.bbcc.kidly.boundedcontext.order.domain.recovery.PaymentRecovery;
import com.bbcc.kidly.global.error.BusinessException;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class PaymentRecoveryServiceTest {

    private static final Long RECOVERY_ID = 3L;
    private static final Long ORDER_ID = 12L;
    private static final Long BUYER_ID = 1L;
    private static final Long PAYMENT_ID = 500L;
    private static final Order.Policy POLICY = new Order.Policy(Duration.ofMinutes(10), Duration.ofDays(15));
    private static final MarkPaidCommand PAID = new MarkPaidCommand(ORDER_ID, PAYMENT_ID, BUYER_ID, 25_000L,
        List.of(new MarkPaidCommand.Item(41L, 25_000L)));

    @Mock
    private PaymentRecoveryRepository recoveryRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderService orderService;

    @Mock
    private Consumer<MarkPaidCommand> applyPaid;

    private PaymentRecoveryService service;

    @BeforeEach
    void setUp() {
        service = new PaymentRecoveryService(recoveryRepository, orderRepository, orderService);
    }

    @Nested
    @DisplayName("실패 보관")
    class RecordFailure {

        @Test
        @DisplayName("주문의 기록이 없으면 받은 결제 완료 내용 · 실패 이유로 PENDING · 시도 1을 새로 저장한다")
        void opensNew() {
            when(recoveryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            PaymentRecoveryResult result = service.recordFailure(PAID, new DataIntegrityViolationException("x"));

            ArgumentCaptor<PaymentRecovery> saved = ArgumentCaptor.forClass(PaymentRecovery.class);
            verify(recoveryRepository).save(saved.capture());
            assertThat(saved.getValue().getOrderId()).isEqualTo(ORDER_ID);
            assertThat(saved.getValue().getPaymentId()).isEqualTo(PAYMENT_ID);
            assertThat(saved.getValue().getMemberId()).isEqualTo(BUYER_ID);
            assertThat(saved.getValue().getAmount()).isEqualTo(25_000L);
            assertThat(saved.getValue().getItems()).containsExactly(new PaymentRecovery.PaidItem(41L, 25_000L));
            assertThat(result.status()).isEqualTo("PENDING");
            assertThat(result.attemptCount()).isEqualTo(1);
            assertThat(result.failureCode()).isEqualTo("DataIntegrityViolationException");
        }

        @Test
        @DisplayName("주문의 기록이 이미 있으면 새로 만들지 않고 시도 +1 · 실패 이유를 바꾼다")
        void countsExisting() {
            PaymentRecovery existing = pending();
            when(recoveryRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existing));

            PaymentRecoveryResult result = service.recordFailure(PAID, new IllegalStateException("x"));

            assertThat(result.attemptCount()).isEqualTo(2);
            assertThat(result.failureCode()).isEqualTo("IllegalStateException");
            assertThat(result.status()).isEqualTo("PENDING");
            verify(recoveryRepository, never()).save(any());
        }

        @Test
        @DisplayName("결제 내용 불일치면 NOT_RETRYABLE · 사유 코드로 저장한다")
        void mismatchIsNotRetryable() {
            when(recoveryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            PaymentRecoveryResult result = service.recordFailure(PAID, new PaymentMismatchException(
                ORDER_ID, PaymentMismatchException.Reason.TOTAL_AMOUNT_MISMATCH, "expected=1 actual=2"));

            assertThat(result.status()).isEqualTo("NOT_RETRYABLE");
            assertThat(result.failureCode()).isEqualTo("total-amount-mismatch");
        }
    }

    @Nested
    @DisplayName("재처리")
    class Recover {

        @Test
        @DisplayName("PENDING이고 주문이 결제대기면 보관한 결제 완료로 결제완료 → 배송 생성 → 장바구니 정리 후 RECOVERED")
        void recoversPending() {
            givenRecovery(pending());
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(placedOrder()));

            PaymentRecoveryResult result = service.recover(RECOVERY_ID, applyPaid);

            verify(applyPaid).accept(PAID);
            assertThat(result.status()).isEqualTo("RECOVERED");
        }

        @Test
        @DisplayName("PENDING인데 주문이 이미 취소됐으면(만료와 경합) 결제완료로 되돌리지 않고 기록을 CANCELLED로 끝낸다")
        void cancelledOrderClosesRecord() {
            givenRecovery(pending());
            Order order = placedOrder();
            order.cancel(Order.CancelReason.PAYMENT_EXPIRED);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            PaymentRecoveryResult result = service.recover(RECOVERY_ID, applyPaid);

            assertThat(result.status()).isEqualTo("CANCELLED");
            verifyNoInteractions(applyPaid);
        }

        @Test
        @DisplayName("PENDING인데 주문이 이미 결제완료면 다시 반영하지 않고 RECOVERED")
        void paidOrderRecovers() {
            givenRecovery(pending());
            Order order = placedOrder();
            order.markPaid(PAYMENT_ID, BUYER_ID, 25_000L, List.of(new Order.PaidItem(41L, 25_000L)),
                LocalDateTime.now());
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            PaymentRecoveryResult result = service.recover(RECOVERY_ID, applyPaid);

            assertThat(result.status()).isEqualTo("RECOVERED");
            verifyNoInteractions(applyPaid);
        }

        @Test
        @DisplayName("이미 RECOVERED면 아무것도 하지 않고 그대로 돌려준다")
        void recoveredStays() {
            PaymentRecovery recovery = pending();
            recovery.recover(LocalDateTime.now());
            givenRecovery(recovery);

            assertThat(service.recover(RECOVERY_ID, applyPaid).status()).isEqualTo("RECOVERED");
            verifyNoInteractions(orderRepository, applyPaid);
        }

        @Test
        @DisplayName("CANCELLED면 RECOVERY_CLOSED, NOT_RETRYABLE이면 RECOVERY_NOT_RETRYABLE, 없으면 RECOVERY_NOT_FOUND")
        void rejected() {
            PaymentRecovery cancelled = pending();
            cancelled.cancel(LocalDateTime.now());
            when(recoveryRepository.findById(1L)).thenReturn(Optional.of(cancelled));
            when(recoveryRepository.findById(2L)).thenReturn(Optional.of(notRetryable()));

            assertError(() -> service.recover(1L, applyPaid), OrderErrorCode.RECOVERY_CLOSED);
            assertError(() -> service.recover(2L, applyPaid), OrderErrorCode.RECOVERY_NOT_RETRYABLE);
            assertError(() -> service.recover(99L, applyPaid), OrderErrorCode.RECOVERY_NOT_FOUND);
            verifyNoInteractions(applyPaid);
        }

        @Test
        @DisplayName("재처리가 다시 실패하면 시도 +1 · 실패 이유를 바꾸고 PENDING으로 남긴다(불일치면 NOT_RETRYABLE)")
        void recordsRetryFailure() {
            givenRecovery(pending());

            PaymentRecoveryResult failed = service.recordRetryFailure(RECOVERY_ID, new IllegalStateException("x"));

            assertThat(failed.status()).isEqualTo("PENDING");
            assertThat(failed.attemptCount()).isEqualTo(2);
            assertThat(failed.failureCode()).isEqualTo("IllegalStateException");

            PaymentRecoveryResult mismatch = service.recordRetryFailure(RECOVERY_ID, new PaymentMismatchException(
                ORDER_ID, PaymentMismatchException.Reason.ITEM_MISSING, "orderItemId=41"));

            assertThat(mismatch.status()).isEqualTo("NOT_RETRYABLE");
            assertThat(mismatch.attemptCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("재처리 실패를 세려는데 그사이 다른 재처리가 복구했으면 기록을 그대로 둔다")
        void retryFailureKeepsClosedRecord() {
            PaymentRecovery recovery = pending();
            recovery.recover(LocalDateTime.now());
            givenRecovery(recovery);

            PaymentRecoveryResult result = service.recordRetryFailure(RECOVERY_ID, new IllegalStateException("x"));

            assertThat(result.status()).isEqualTo("RECOVERED");
            assertThat(result.attemptCount()).isEqualTo(1);
            assertThat(result.failureCode()).isEqualTo("DataIntegrityViolationException");
        }
    }

    @Nested
    @DisplayName("취소로 종료")
    class Cancel {

        @Test
        @DisplayName("PENDING · NOT_RETRYABLE이면 주문을 결제 실패로 취소(order.cancelled)하고 기록을 CANCELLED로 끝낸다")
        void cancelsOpenRecord() {
            when(recoveryRepository.findById(1L)).thenReturn(Optional.of(pending()));
            when(recoveryRepository.findById(2L)).thenReturn(Optional.of(notRetryable()));
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(placedOrder()));

            assertThat(service.cancel(1L).status()).isEqualTo("CANCELLED");
            assertThat(service.cancel(2L).status()).isEqualTo("CANCELLED");
            verify(orderService, org.mockito.Mockito.times(2)).cancelForPaymentFailure(ORDER_ID);
        }

        @Test
        @DisplayName("이미 CANCELLED면 주문을 다시 취소하지 않고 그대로 돌려준다")
        void cancelledStays() {
            PaymentRecovery recovery = pending();
            recovery.cancel(LocalDateTime.now());
            givenRecovery(recovery);

            assertThat(service.cancel(RECOVERY_ID).status()).isEqualTo("CANCELLED");
            verifyNoInteractions(orderService);
        }

        @Test
        @DisplayName("RECOVERED면 RECOVERY_CLOSED, PENDING인데 주문이 결제완료면 RECOVERY_ORDER_PAID, 없으면 RECOVERY_NOT_FOUND")
        void rejected() {
            PaymentRecovery recovered = pending();
            recovered.recover(LocalDateTime.now());
            when(recoveryRepository.findById(1L)).thenReturn(Optional.of(recovered));
            when(recoveryRepository.findById(2L)).thenReturn(Optional.of(pending()));
            Order paid = placedOrder();
            paid.markPaid(PAYMENT_ID, BUYER_ID, 25_000L, List.of(new Order.PaidItem(41L, 25_000L)),
                LocalDateTime.now());
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(paid));

            assertError(() -> service.cancel(1L), OrderErrorCode.RECOVERY_CLOSED);
            assertError(() -> service.cancel(2L), OrderErrorCode.RECOVERY_ORDER_PAID);
            assertError(() -> service.cancel(99L), OrderErrorCode.RECOVERY_NOT_FOUND);
            verifyNoInteractions(orderService);
        }
    }

    @Nested
    @DisplayName("목록")
    class GetRecoveries {

        @Test
        @DisplayName("상태를 고르지 않으면 모든 기록, 고르면 그 상태만 최근 기록부터")
        void filtersByStatus() {
            PageRequest pageable = PageRequest.of(0, 20);
            when(recoveryRepository.findAllByOrderByIdDesc(pageable)).thenReturn(new PageImpl<>(List.of(pending())));
            when(recoveryRepository.findAllByStatusOrderByIdDesc(PaymentRecovery.Status.NOT_RETRYABLE, pageable))
                .thenReturn(new PageImpl<>(List.of(notRetryable())));

            assertThat(service.getRecoveries(null, 0, 20).getContent())
                .extracting(PaymentRecoveryResult::status).containsExactly("PENDING");
            assertThat(service.getRecoveries("NOT_RETRYABLE", 0, 20).getContent())
                .extracting(PaymentRecoveryResult::status).containsExactly("NOT_RETRYABLE");
        }

        @ParameterizedTest(name = "status={0}")
        @ValueSource(strings = {"pending", "UNKNOWN", ""})
        @DisplayName("상태 이름이 정확히 맞지 않으면(소문자 · 모르는 값 · 빈 값) INVALID_RECOVERY_STATUS, 조회하지 않는다")
        void rejectsUnknownStatus(String status) {
            assertError(() -> service.getRecoveries(status, 0, 20), OrderErrorCode.INVALID_RECOVERY_STATUS);
            verifyNoInteractions(recoveryRepository);
        }

        @Test
        @DisplayName("offset이 int를 넘는 큰 page는 조회 없이 빈 목록, 전체 건수는 실제 건수")
        void bigPageIsEmpty() {
            when(recoveryRepository.countByStatus(PaymentRecovery.Status.PENDING)).thenReturn(7L);
            when(recoveryRepository.count()).thenReturn(9L);

            Page<PaymentRecoveryResult> filtered = service.getRecoveries("PENDING",
                Integer.MAX_VALUE, 100);
            Page<PaymentRecoveryResult> all = service.getRecoveries(null, Integer.MAX_VALUE, 100);

            assertThat(filtered.getContent()).isEmpty();
            assertThat(filtered.getTotalElements()).isEqualTo(7L);
            assertThat(all.getTotalElements()).isEqualTo(9L);
            verify(recoveryRepository, never()).findAllByOrderByIdDesc(any());
            verify(recoveryRepository, never()).findAllByStatusOrderByIdDesc(any(), any());
        }
    }

    private void givenRecovery(PaymentRecovery recovery) {
        when(recoveryRepository.findById(RECOVERY_ID)).thenReturn(Optional.of(recovery));
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, OrderErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(code);
    }

    private static PaymentRecovery pending() {
        return PaymentRecovery.open(ORDER_ID, PAYMENT_ID, BUYER_ID, 25_000L,
            List.of(new PaymentRecovery.PaidItem(41L, 25_000L)), "DataIntegrityViolationException", true,
            LocalDateTime.now());
    }

    private static PaymentRecovery notRetryable() {
        return PaymentRecovery.open(ORDER_ID, PAYMENT_ID, BUYER_ID, 25_000L,
            List.of(new PaymentRecovery.PaidItem(41L, 25_000L)), "member-mismatch", false, LocalDateTime.now());
    }

    private static Order placedOrder() {
        Order order = Order.place(BUYER_ID, List.of(new Order.RequestedItem(new OrderItem.ProductSnapshot(
                1L, 1L, 2L, "상품 A", null, "110", 25_000L, 1L), true, null, 1, 25_000L)),
            new Order.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1"), POLICY,
            LocalDateTime.now());
        setField(Order.class, order, "id", ORDER_ID);
        setField(OrderItem.class, order.getItems().get(0), "id", 41L);
        return order;
    }

    private static void setField(Class<?> type, Object target, String name, Object value) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
