package com.bbcc.kidly.boundedcontext.order.in.event;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.DeliveryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * 결제 ID 없이 온 결제 완료: 돈은 빠졌으므로 결제완료로 바꾸고(결제 ID는 비어 있음) ERROR를 남긴다.
 * 리스너 메서드를 직접 부른다(자체 트랜잭션). 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class PaymentMissingIdIntegrationTest {

    private static final Long BUYER_ID = 5_100L;

    @Autowired
    private PaymentEventListener paymentEventListener;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Test
    @DisplayName("결제 ID가 없는 결제 완료 → 주문은 PAID(결제 ID 비어 있음) · 판매자 배송 1건 생성, ERROR는 정확히 한 줄(reason=missing-payment-id)")
    void missingPaymentIdStillPaid(CapturedOutput output) {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        OrderDetail order = orderService.place(BUYER_ID, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, null, 1, 25_000L)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1")));
        LocalDateTime now = LocalDateTime.now();

        paymentEventListener.onPaymentCompleted(new PaymentCompletedEvent(UUID.randomUUID(), now,
            new PaymentCompletedEvent.Data(null, order.orderId(), BUYER_ID, order.totalAmount(),
                order.items().stream()
                    .map(item -> new PaymentCompletedEvent.Item(item.orderItemId(), item.amount()))
                    .toList(),
                now)));

        Order stored = orderRepository.findById(order.orderId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(Order.Status.PAID);
        assertThat(stored.getPaymentId()).isNull();
        assertThat(deliveryRepository.findAllByOrderIdOrderByIdAsc(order.orderId())).hasSize(1);
        String errorLine = "event=payment.completed reason=missing-payment-id orderId=" + order.orderId();
        assertThat(output.getAll().lines()
            .filter(line -> line.contains("ERROR") && line.contains(errorLine)))
            .hasSize(1);
    }
}
