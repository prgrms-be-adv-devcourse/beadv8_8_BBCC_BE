package com.bbcc.kidly.boundedcontext.order.in.scheduler;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.MarkPaidCommands;
import com.bbcc.kidly.boundedcontext.order.ConcurrentCalls;
import com.bbcc.kidly.boundedcontext.order.OrderDeliveredAtSetter;
import com.bbcc.kidly.boundedcontext.order.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 직접 구매확정 ×2와 자동 구매확정의 동시 요청. 테스트에 @Transactional을 붙이지 않는다(작업마다 자기 트랜잭션으로 커밋).
 * 반복마다 구매자를 바꿔 새 주문으로 시작하고, 이벤트는 그 주문 ID로 걸러 센다.
 */
@IntegrationTest
class ConfirmConcurrencyIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(ConfirmConcurrencyIntegrationTest.class);
    private static final int REPEAT = 10;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderDeliveredAtSetter deliveredAtSetter;

    @Autowired
    private Order.Policy orderPolicy;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private OrderFlowEventRecorder recorder;

    @BeforeEach
    void setUp() {
        // 테스트마다 주문 ID가 1부터 다시 시작해(RESTART IDENTITY) 다른 테스트의 같은 orderId 이벤트가 섞이지 않게 비운다
        recorder.clear();
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
    }

    @Test
    @DisplayName("직접 확정 ×2와 자동 확정이 동시에 와도 한 번만 확정되고 purchase-confirmed도 한 번이다")
    void confirmTwiceAndAutoConfirm() throws InterruptedException {
        for (int round = 1; round <= REPEAT; round++) {
            Long buyerId = 3_000L + round;
            OrderDetail order = deliveredPastPeriod(buyerId);
            Long orderItemId = order.items().get(0).orderItemId();

            ConcurrentCalls.Outcome outcome = ConcurrentCalls.run(List.of(
                () -> orderService.confirmByBuyer(buyerId, order.orderId(), orderItemId),
                () -> orderService.confirmByBuyer(buyerId, order.orderId(), orderItemId),
                () -> orderService.confirmAutomatically(order.orderId(), orderItemId)));

            assertThat(outcome.unexpected()).as("낙관적 락 말고 다른 예외(%d회차)", round).isEmpty();
            OrderDetail.Item item = orderService.getOrder(buyerId, order.orderId()).items().stream()
                .filter(candidate -> candidate.orderItemId().equals(orderItemId))
                .findFirst()
                .orElseThrow();
            List<OrderItemPurchaseConfirmedEvent> confirmed = recorder.events(OrderItemPurchaseConfirmedEvent.class)
                .stream()
                .filter(event -> event.data().orderId().equals(order.orderId()))
                .toList();
            LOG.info("[#137] 직접 확정 ×2 + 자동 확정 {}회차: 확정 방식 {}, 이벤트 {}번, 낙관적 락 충돌 {}회",
                round, item.confirmType(), confirmed.size(), outcome.conflicts());

            assertThat(item.status()).isEqualTo("CONFIRMED");
            assertThat(item.confirmType()).isIn("DIRECT", "AUTO");
            assertThat(confirmed).hasSize(1);
            assertThat(confirmed.get(0).data().orderItemId()).isEqualTo(orderItemId);
        }
    }

    /**
     * 주문(자녀 없음) → 결제완료 → 판매자 배송 완료(자동 확정 기간이 지난 시각). 서비스를 바로 불러 상태만 만든다.
     */
    private OrderDetail deliveredPastPeriod(Long buyerId) {
        OrderDetail order = orderService.place(buyerId, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, null, 1, 25_000L)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1")));
        orderService.markPaid(MarkPaidCommands.matching(order, buyerId, 500L));
        deliveredAtSetter.recordDelivered(order.orderId(), SELLER_ID,
            LocalDateTime.now().minus(orderPolicy.autoConfirmAfter()).minusMinutes(1));
        return order;
    }
}
