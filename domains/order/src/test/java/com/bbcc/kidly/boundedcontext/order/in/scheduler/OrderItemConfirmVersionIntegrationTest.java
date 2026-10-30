package com.bbcc.kidly.boundedcontext.order.in.scheduler;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.order.MarkPaidCommands;
import com.bbcc.kidly.boundedcontext.order.OrderDeliveredAtSetter;
import com.bbcc.kidly.boundedcontext.order.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import com.bbcc.kidly.boundedcontext.order.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 같은 주문상품을 두 경로가 같은 버전으로 읽은 뒤 차례로 확정할 때, 나중 쪽이 낙관적 락으로 실패하는지 본다(동시 실행 없이).
 * 바깥 트랜잭션이 주문을 먼저 읽어 두고 → 새 트랜잭션에서 직접 확정을 커밋 → 바깥에서 읽어 둔(낡은) 주문으로 자동 확정을 커밋한다.
 * 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
class OrderItemConfirmVersionIntegrationTest {

    private static final Long BUYER_ID = 4_000L;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderDeliveredAtSetter deliveredAtSetter;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private Order.Policy orderPolicy;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private OrderFlowEventRecorder recorder;

    @BeforeEach
    void setUp() {
        // 테스트마다 주문 ID가 1부터 다시 시작해(RESTART IDENTITY) 다른 테스트의 같은 orderId 이벤트가 섞이지 않게 비운다
        recorder.clear();
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
    }

    @Test
    @DisplayName("같은 버전으로 읽은 주문상품을 나중에 확정하면 낙관적 락으로 실패하고, 확정 · purchase-confirmed는 한 번이다")
    void staleConfirmFails() {
        OrderDetail order = deliveredPastPeriod();
        Long orderItemId = order.items().get(0).orderItemId();
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        TransactionTemplate separate = new TransactionTemplate(transactionManager);
        separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> outer.executeWithoutResult(status -> {
            // 바깥 트랜잭션이 주문 · 주문상품을 먼저 읽어 둔다(이후 같은 트랜잭션의 조회는 이 객체를 다시 쓴다)
            Order stale = orderRepository.findById(order.orderId()).orElseThrow();
            assertThat(stale.getItems()).hasSize(1);

            separate.executeWithoutResult(inner -> orderService.confirmByBuyer(BUYER_ID, order.orderId(), orderItemId));

            orderService.confirmAutomatically(order.orderId(), orderItemId);
        })).isInstanceOf(OptimisticLockingFailureException.class);

        OrderDetail.Item item = orderService.getOrder(BUYER_ID, order.orderId()).items().stream()
            .filter(candidate -> candidate.orderItemId().equals(orderItemId))
            .findFirst()
            .orElseThrow();
        assertThat(item.status()).isEqualTo("CONFIRMED");
        assertThat(item.confirmType()).isEqualTo("DIRECT");
        List<OrderItemPurchaseConfirmedEvent> confirmed = recorder.events(OrderItemPurchaseConfirmedEvent.class)
            .stream()
            .filter(event -> event.data().orderId().equals(order.orderId()))
            .toList();
        assertThat(confirmed).hasSize(1);
        assertThat(confirmed.get(0).data().orderItemId()).isEqualTo(orderItemId);
    }

    /**
     * 주문(자녀 없음) → 결제완료 → 판매자 배송 완료(자동 확정 기간이 지난 시각). 서비스를 바로 불러 상태만 만든다.
     */
    private OrderDetail deliveredPastPeriod() {
        OrderDetail order = orderService.place(BUYER_ID, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, null, 1, 25_000L)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1")));
        orderService.markPaid(MarkPaidCommands.matching(order, BUYER_ID, 500L));
        deliveredAtSetter.recordDelivered(order.orderId(), SELLER_ID,
            LocalDateTime.now().minus(orderPolicy.autoConfirmAfter()).minusMinutes(1));
        return order;
    }
}
