package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.ORDER_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.bbcc.kidly.boundedcontext.payment.app.OrderCancellationRetryService;
import com.bbcc.kidly.boundedcontext.payment.app.OrderCancellationService;
import com.bbcc.kidly.boundedcontext.payment.app.OrderPaymentService;
import com.bbcc.kidly.boundedcontext.payment.app.PaymentEventHandler;
import com.bbcc.kidly.boundedcontext.payment.app.SettlementPayoutService;
import com.bbcc.kidly.boundedcontext.payment.app.dto.OrderCancelCommand;
import com.bbcc.kidly.boundedcontext.payment.app.dto.PayOrderCommand;
import com.bbcc.kidly.boundedcontext.payment.app.dto.PayoutCommand;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.shared.member.event.MemberRegisteredEvent;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import com.bbcc.kidly.shared.order.event.OrderPaymentRequestedEvent;
import com.bbcc.kidly.shared.settlement.event.SettlementPayoutRequestedEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 결제가 받는 이벤트의 처리. Spring 리스너와 Kafka 리스너가 같은 처리를 부른다(#593).
 */
@ExtendWith(MockitoExtension.class)
class PaymentEventHandlerTest {

    private static final OrderCancelCommand COMMAND = new OrderCancelCommand(ORDER_ID, MEMBER_ID, "PAYMENT_EXPIRED");

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private OrderPaymentService orderPaymentService;

    @Mock
    private OrderCancellationService orderCancellationService;

    @Mock
    private OrderCancellationRetryService orderCancellationRetryService;

    @Mock
    private SettlementPayoutService settlementPayoutService;

    @InjectMocks
    private PaymentEventHandler handler;

    @Test
    @DisplayName("회원이 가입하면 그 회원의 지갑을 없을 때만 만든다")
    void memberRegistered() {
        handler.memberRegistered(new MemberRegisteredEvent(UUID.randomUUID(), LocalDateTime.now(),
            new MemberRegisteredEvent.Data(MEMBER_ID, "buyer@kidly.test")));

        then(walletRepository).should().createIfAbsent(eq(MEMBER_ID), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("결제 요청은 주문 · 회원 · 총액 · 주문상품별 금액으로 결제한다")
    void orderPaymentRequested() {
        handler.orderPaymentRequested(new OrderPaymentRequestedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new OrderPaymentRequestedEvent.Data(ORDER_ID, MEMBER_ID, 15_000L, List.of(
                new OrderPaymentRequestedEvent.Item(1000L, 10_000L),
                new OrderPaymentRequestedEvent.Item(1001L, 5_000L)))));

        then(orderPaymentService).should().pay(new PayOrderCommand(ORDER_ID, MEMBER_ID, 15_000L, List.of(
            new PayOrderCommand.Item(1000L, 10_000L), new PayOrderCommand.Item(1001L, 5_000L))));
    }

    @Test
    @DisplayName("취소 처리가 끝나면 다시 처리할 기록을 남기지 않는다")
    void orderCancelled() {
        handler.orderCancelled(orderCancelledEvent());

        then(orderCancellationService).should().handle(COMMAND);
        then(orderCancellationRetryService).should(never()).remember(any(), any());
    }

    @Test
    @DisplayName("취소 처리가 실패하면 예외를 삼키고 다시 처리할 취소로 남긴다")
    void rememberFailure() {
        IllegalStateException failure = new IllegalStateException("DB 오류");
        willThrow(failure).given(orderCancellationService).handle(COMMAND);

        handler.orderCancelled(orderCancelledEvent());

        then(orderCancellationRetryService).should().remember(COMMAND, failure);
    }

    @Test
    @DisplayName("정산 지급 요청은 정산서 · 판매자 · 정산금액으로 판매자 예치금에 입금한다")
    void settlementPayoutRequested() {
        handler.settlementPayoutRequested(new SettlementPayoutRequestedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new SettlementPayoutRequestedEvent.Data(31L, MEMBER_ID, 270_000L, LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31))));

        then(settlementPayoutService).should().payout(new PayoutCommand(31L, MEMBER_ID, 270_000L));
    }

    private static OrderCancelledEvent orderCancelledEvent() {
        return new OrderCancelledEvent(UUID.randomUUID(), LocalDateTime.now(),
            new OrderCancelledEvent.Data(ORDER_ID, MEMBER_ID, "PAYMENT_EXPIRED", List.of(
                new OrderCancelledEvent.Item(1000L, 1L, 1))));
    }
}
