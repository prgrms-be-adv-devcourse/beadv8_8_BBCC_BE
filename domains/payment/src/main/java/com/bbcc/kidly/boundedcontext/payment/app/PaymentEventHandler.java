package com.bbcc.kidly.boundedcontext.payment.app;

import com.bbcc.kidly.boundedcontext.payment.app.dto.OrderCancelCommand;
import com.bbcc.kidly.boundedcontext.payment.app.dto.PayOrderCommand;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.shared.member.event.MemberRegisteredEvent;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import com.bbcc.kidly.shared.order.event.OrderPaymentRequestedEvent;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 결제가 받는 회원 · 주문 이벤트의 처리(#593). Kafka 리스너(in.event)와 Spring 리스너(스위치를 끈 경우)가 같은 처리를 부른다.
 * 이벤트 필드가 바뀌면 여기 변환만 고친다. 각 처리는 자기 트랜잭션을 연다(지갑 만들기 · 결제 · 취소 · 다시 처리할 기록).
 * 같은 이벤트를 다시 받아도 결과는 1회분이다: 지갑은 없을 때만 만들고, 주문당 결제는 한 번이며, 같은 취소는 한 번만 환불한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventHandler {

    private final WalletRepository walletRepository;
    private final OrderPaymentService orderPaymentService;
    private final OrderCancellationService orderCancellationService;
    private final OrderCancellationRetryService orderCancellationRetryService;

    /**
     * 회원이 가입하면 잔액 0인 예치금 지갑을 만든다. 같은 회원의 이벤트를 다시 받거나 동시에 받아도 지갑은 하나다.
     * 이 처리가 실패해도 지갑을 쓰는 곳(충전 요청 · 결제 · 입금)이 없으면 만든다.
     */
    public void memberRegistered(MemberRegisteredEvent event) {
        walletRepository.createIfAbsent(event.data().memberId(), LocalDateTime.now());
    }

    /**
     * order.payment-requested: 주문을 예치금으로 결제한다.
     */
    public void orderPaymentRequested(OrderPaymentRequestedEvent event) {
        OrderPaymentRequestedEvent.Data data = event.data();
        orderPaymentService.pay(new PayOrderCommand(data.orderId(), data.memberId(), data.amount(),
            data.items().stream()
                .map(item -> new PayOrderCommand.Item(item.orderItemId(), item.amount()))
                .toList()));
    }

    /**
     * order.cancelled: 결제된 주문은 환불하고, 결제 전이면 그 뒤 결제 요청을 거절하게 한다.
     * 처리는 별도 트랜잭션이라 실패하면 처리한 것만 롤백된다. 실패한 취소는 남겨 결제 스케줄러가 다시 처리한다(#350).
     * 남기기까지 실패하면 예외가 나가 받는 쪽이 다시 시도한다(Kafka: 재시도 뒤 DLT).
     */
    public void orderCancelled(OrderCancelledEvent event) {
        OrderCancelledEvent.Data data = event.data();
        OrderCancelCommand command = new OrderCancelCommand(data.orderId(), data.memberId(), data.reason());
        try {
            orderCancellationService.handle(command);
        } catch (RuntimeException e) {
            log.error("주문 취소를 처리하지 못해 다시 처리할 취소로 남깁니다. orderId={}", command.orderId(), e);
            orderCancellationRetryService.remember(command, e);
        }
    }
}
