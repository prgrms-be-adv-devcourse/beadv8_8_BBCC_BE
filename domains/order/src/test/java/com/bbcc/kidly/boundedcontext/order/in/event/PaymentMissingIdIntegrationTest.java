package com.bbcc.kidly.boundedcontext.order.in.event;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.order.OrderEventDelivery;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.PaymentMismatchException;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.order.support.IntegrationTest;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * 깨진 값이 온 결제 완료(#251): 결제 ID · 결제 시각이 없으면 돈은 빠졌으므로 결제완료로 바꾸고 ERROR를 남긴다.
 * 회원 ID · 주문상품 목록 · 주문상품 ID가 없으면 #215 대조 실패로 결제완료가 되지 않는다.
 * 리스너 메서드를 직접 부른다(자체 트랜잭션). 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class PaymentMissingIdIntegrationTest {

    private static final Long BUYER_ID = 5_100L;

    @Autowired
    private OrderEventDelivery eventDelivery;

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

        eventDelivery.invoke(new PaymentCompletedEvent(UUID.randomUUID(), now,
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

    @Test
    @DisplayName("결제 시각이 없는 결제 완료 → 주문은 PAID(결제 시각은 처리 시각) · 배송 1건, ERROR는 정확히 한 줄(reason=missing-paid-at)")
    void missingPaidAtStillPaid(CapturedOutput output) {
        OrderDetail order = placeOrder();
        LocalDateTime now = LocalDateTime.now();

        eventDelivery.invoke(new PaymentCompletedEvent(UUID.randomUUID(), now,
            new PaymentCompletedEvent.Data(500L, order.orderId(), BUYER_ID, order.totalAmount(), itemsOf(order),
                null)));

        Order stored = orderRepository.findById(order.orderId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(Order.Status.PAID);
        assertThat(stored.getPaidAt()).isNotNull();
        assertThat(deliveryRepository.findAllByOrderIdOrderByIdAsc(order.orderId())).hasSize(1);
        String errorLine = "event=payment.completed reason=missing-paid-at orderId=" + order.orderId();
        assertThat(output.getAll().lines()
            .filter(line -> line.contains("ERROR") && line.contains(errorLine)))
            .hasSize(1);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "memberId, MEMBER_MISMATCH",
        "items, ITEM_MISSING",
        "orderItemId, ITEM_EXTRA",
        "onlyNullOrderItemId, ITEM_MISSING"
    })
    @DisplayName("회원 ID · 주문상품 목록 · 주문상품 ID가 없는 결제 완료는 #215 대조 실패(사유 코드)로 PAID가 되지 않고 결제대기 · 배송 0건")
    void missingMatchValuesAreNotPaid(String missing, PaymentMismatchException.Reason expected) {
        OrderDetail order = placeOrder();
        LocalDateTime now = LocalDateTime.now();
        Long memberId = missing.equals("memberId") ? null : BUYER_ID;
        List<PaymentCompletedEvent.Item> items = switch (missing) {
            case "items" -> null;
            // 실제 주문상품은 모두 오고 ID 없는 줄이 하나 더 붙은 경우: 구성은 채워졌지만 남는 줄
            case "orderItemId" -> Stream.concat(itemsOf(order).stream(),
                Stream.of(new PaymentCompletedEvent.Item(null, 0L))).toList();
            // ID 없는 줄만 온 경우: 대조 순서(회원 → 빠짐 → 남음)상 주문상품이 빠진 것이 먼저 걸린다
            case "onlyNullOrderItemId" -> List.of(new PaymentCompletedEvent.Item(null, order.totalAmount()));
            default -> itemsOf(order);
        };

        assertThatThrownBy(() -> eventDelivery.invoke(
            new PaymentCompletedEvent(UUID.randomUUID(), now,
                new PaymentCompletedEvent.Data(500L, order.orderId(), memberId, order.totalAmount(), items, now))))
            .isInstanceOfSatisfying(PaymentMismatchException.class, e -> assertThat(e.getReason()).isEqualTo(expected));

        assertThat(orderRepository.findById(order.orderId()).orElseThrow().getStatus())
            .isEqualTo(Order.Status.PENDING_PAYMENT);
        assertThat(deliveryRepository.findAllByOrderIdOrderByIdAsc(order.orderId())).isEqualTo(List.of());
    }

    private OrderDetail placeOrder() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        return orderService.place(BUYER_ID, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, null, 1, 25_000L)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1")));
    }

    private static List<PaymentCompletedEvent.Item> itemsOf(OrderDetail order) {
        return order.items().stream()
            .map(item -> new PaymentCompletedEvent.Item(item.orderItemId(), item.amount()))
            .toList();
    }
}
