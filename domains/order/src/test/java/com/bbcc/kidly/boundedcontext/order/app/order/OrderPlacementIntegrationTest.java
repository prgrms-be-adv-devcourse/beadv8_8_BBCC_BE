package com.bbcc.kidly.boundedcontext.order.app.order;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.order.ConcurrentCalls;
import com.bbcc.kidly.boundedcontext.order.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.order.app.OrderErrorCode;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderResult;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.order.event.OrderCreatedEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 요청 키(#351)의 DB 유니크 제약과 동시 요청. 테스트에 @Transactional을 붙이지 않는다(작업마다 자기 트랜잭션으로 커밋).
 * 동시 요청은 반복마다 구매자를 바꾸고, 결과(주문 수 · order.created 수 · 응답)만 본다(장벽 · sleep 없음).
 */
@IntegrationTest
class OrderPlacementIntegrationTest {

    private static final int REPEAT = 10;
    private static final Long BUYER_ID = 1L;

    @Autowired
    private OrderPlacement orderPlacement;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrderFlowEventRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder.clear();
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
    }

    @Test
    @DisplayName("키 없이 만든 주문은 키 · 비교값이 null이고 같은 회원이 여러 번 만들 수 있다(null끼리는 유니크에 걸리지 않음)")
    void ordersWithoutKeyCoexist() {
        Long first = orderPlacement.place(BUYER_ID, command(1), null).detail().orderId();
        Long second = orderPlacement.place(BUYER_ID, command(1), null).detail().orderId();

        assertThat(first).isNotEqualTo(second);
        Order stored = orderRepository.findById(first).orElseThrow();
        assertThat(stored.getIdempotencyKey()).isNull();
        assertThat(stored.getRequestHash()).isNull();
    }

    @Test
    @DisplayName("같은 회원이 같은 키를 두 주문에 쓰면 DB가 uk_orders_member_idempotency_key로 막는다")
    void databaseRejectsSameKeyTwice() {
        String key = UUID.randomUUID().toString();
        PlaceOrderResult keyed = orderPlacement.place(BUYER_ID, command(1), key);
        Long other = orderPlacement.place(BUYER_ID, command(1), null).detail().orderId();

        Order stored = orderRepository.findById(keyed.detail().orderId()).orElseThrow();
        assertThat(stored.getIdempotencyKey()).isEqualTo(key);
        assertThat(stored.getRequestHash()).matches("[0-9a-f]{64}");
        assertThatThrownBy(() -> jdbcTemplate.update(
            "update orders.orders set idempotency_key = ? where id = ?", key, other))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining(Order.MEMBER_IDEMPOTENCY_KEY_UNIQUE);
    }

    @Test
    @DisplayName("같은 키 · 같은 요청 둘이 동시에 와도 주문 · order.created는 하나이고 둘 다 그 주문을 돌려준다(하나만 created)")
    void concurrentSameRequest() throws InterruptedException {
        for (int round = 1; round <= REPEAT; round++) {
            Long buyerId = 1_000L + round;
            String key = UUID.randomUUID().toString();
            List<PlaceOrderResult> results = new CopyOnWriteArrayList<>();

            ConcurrentCalls.Outcome outcome = ConcurrentCalls.run(List.of(
                () -> results.add(orderPlacement.place(buyerId, command(1), key)),
                () -> results.add(orderPlacement.place(buyerId, command(1), key))));

            assertThat(outcome.unexpected()).as("%d회차 예외", round).isEmpty();
            assertThat(outcome.conflicts()).as("%d회차 진 쪽 예외", round).isZero();
            assertThat(orderRepository.countByMemberId(buyerId)).as("%d회차 주문 수", round).isEqualTo(1);
            assertThat(results).as("%d회차 응답", round).hasSize(2)
                .extracting(result -> result.detail().orderId()).containsOnly(results.get(0).detail().orderId());
            assertThat(results).filteredOn(PlaceOrderResult::created).as("%d회차 새로 만든 응답", round).hasSize(1);
            // 진 쪽 트랜잭션은 되돌려지므로 그 주문하기 이력도 남지 않는다(#442, 이력에는 주문 FK가 없어 주체로 센다)
            assertThat(jdbcTemplate.queryForObject("select count(*) from orders.order_status_history "
                + "where actor_id = ? and source = 'POST /api/v1/orders'", Long.class, buyerId))
                .as("%d회차 주문하기 이력", round).isEqualTo(1);
            assertThat(createdEventsOf(buyerId)).as("%d회차 order.created", round).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("같은 키 · 다른 요청 둘이 동시에 와도 주문은 하나이고 다른 쪽은 IDEMPOTENCY_KEY_REUSED")
    void concurrentDifferentRequest() throws InterruptedException {
        for (int round = 1; round <= REPEAT; round++) {
            Long buyerId = 1_000L + round;
            String key = UUID.randomUUID().toString();
            List<PlaceOrderResult> results = new CopyOnWriteArrayList<>();

            ConcurrentCalls.Outcome outcome = ConcurrentCalls.run(List.of(
                () -> results.add(orderPlacement.place(buyerId, command(1), key)),
                () -> results.add(orderPlacement.place(buyerId, command(2), key))),
                OrderPlacementIntegrationTest::isKeyReused);

            assertThat(outcome.unexpected()).as("%d회차 예외", round).isEmpty();
            assertThat(outcome.conflicts()).as("%d회차 409", round).isEqualTo(1);
            assertThat(results).as("%d회차 성공 응답", round).hasSize(1)
                .allSatisfy(result -> assertThat(result.created()).isTrue());
            assertThat(orderRepository.countByMemberId(buyerId)).as("%d회차 주문 수", round).isEqualTo(1);
            assertThat(createdEventsOf(buyerId)).as("%d회차 order.created", round).isEqualTo(1);
        }
    }

    private long createdEventsOf(Long buyerId) {
        return recorder.events(OrderCreatedEvent.class).stream()
            .filter(event -> event.data().memberId().equals(buyerId))
            .count();
    }

    private static boolean isKeyReused(Throwable error) {
        return Optional.of(error)
            .filter(BusinessException.class::isInstance)
            .map(BusinessException.class::cast)
            .filter(exception -> exception.getErrorCode() == OrderErrorCode.IDEMPOTENCY_KEY_REUSED)
            .isPresent();
    }

    private static PlaceOrderCommand command(int quantity) {
        return new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, null, quantity, 25_000L)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1"));
    }
}
