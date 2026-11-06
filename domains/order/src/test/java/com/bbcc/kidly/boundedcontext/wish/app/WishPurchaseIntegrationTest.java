package com.bbcc.kidly.boundedcontext.wish.app;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.cart.CartOwners;
import com.bbcc.kidly.boundedcontext.cart.app.CartService;
import com.bbcc.kidly.boundedcontext.cart.app.port.in.CartCleanup;
import com.bbcc.kidly.boundedcontext.order.ConcurrentCalls;
import com.bbcc.kidly.boundedcontext.order.OrderEventDelivery;
import com.bbcc.kidly.boundedcontext.order.PaymentEvents;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderQueryService;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import com.bbcc.kidly.boundedcontext.wish.app.WishPurchaseService.PurchasedLine;
import com.bbcc.kidly.boundedcontext.wish.out.actor.ChildWishActor;
import com.bbcc.kidly.boundedcontext.wish.out.actor.ParentWishActor;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 조르기 결제 완료(#999): 장바구니 구매 사실(칸 · 주문 · 예약 ID)을 받아 조르기 예약 칸이면 결제 완료로 바꾼다. 수신 경로(Kafka · Spring)와
 * 상관없는 처리 규칙을 처리 메서드로 직접 본다: 같은 주문의 여러 칸 · 다시 받기 · 순서 바뀜도 1회분, 남의 예약 · 남의 칸은 무시,
 * 거절 · 철회된 조르기는 산 것만 남김, 결제 반영 전의 거절 · 철회는 409. 그리고 조르기 처리가 실패해도 결제 완료는 되돌아가지 않는다(결제 정리 트랜잭션과 분리).
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class WishPurchaseIntegrationTest {

    private static final Long CHILD_ID = 1L;
    private static final ChildWishActor CHILD = new ChildWishActor(CHILD_ID, BUYER_ID);
    private static final ParentWishActor GUARDIAN = new ParentWishActor(BUYER_ID);
    private static final long PRICE = 25_000L;
    private static final Long ORDER_ID = 900L;

    @Autowired
    private WishRequestService wishRequestService;

    @Autowired
    private WishPurchaseService wishPurchaseService;

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderQueryService orderQueries;

    @Autowired
    private OrderEventDelivery eventDelivery;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private ChildReplicaRepository childReplicaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(PRICE, OCCURRED_AT)));
        productReplicaRepository.save(ReplicaProduct.create(
            ReplicaFixture.product(2L, 12_000L, "ON_SALE", false, OCCURRED_AT)));
        childReplicaRepository.save(ReplicaChild.create(ReplicaFixture.child("115.0", false, OCCURRED_AT)));
    }

    @Test
    @DisplayName("같은 주문의 여러 칸 · 같은 칸 다시 받기: 처음 칸에서 한 번만 PAID · wish.paid, 산 칸은 칸마다 한 번, 남은 예약은 풀린다")
    void manyLinesAndDuplicatesOnce() {
        Long first = cartLine(1L, 2);
        Long second = cartLine(2L, 1);
        Long requestId = wishRequestService.send(CHILD, List.of(first, second), null).requestId();

        assertThat(wishPurchaseService.onPurchased(purchased(first, requestId))).isTrue();
        assertThat(wishPurchaseService.onPurchased(purchased(first, requestId))).isFalse();
        assertThat(wishPurchaseService.onPurchased(purchased(second, requestId))).isFalse();
        assertThat(wishPurchaseService.onPurchased(purchased(second, requestId))).isFalse();

        assertThat(status(requestId)).isEqualTo("PAID");
        assertThat(jdbcTemplate.queryForObject("select payer_type || ':' || payer_id from orders.wish_request "
            + "where id = ?", String.class, requestId)).isEqualTo("MEMBER:" + BUYER_ID);
        assertThat(jdbcTemplate.queryForList("select cart_item_id from orders.wish_purchase where request_id = ? "
            + "order by id", Long.class, requestId)).containsExactly(first, second);
        assertThat(eventCount("wish.paid", requestId)).isOne();
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.cart_reservation", Long.class)).isZero();
    }

    @Test
    @DisplayName("순서가 바뀌어 들어와도(두 번째 칸 먼저) 결과는 같다")
    void outOfOrderSameResult() {
        Long first = cartLine(1L, 1);
        Long second = cartLine(2L, 1);
        Long requestId = wishRequestService.send(CHILD, List.of(first, second), null).requestId();

        assertThat(wishPurchaseService.onPurchased(purchased(second, requestId))).isTrue();
        assertThat(wishPurchaseService.onPurchased(purchased(first, requestId))).isFalse();

        assertThat(status(requestId)).isEqualTo("PAID");
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.wish_purchase where request_id = ?",
            Long.class, requestId)).isEqualTo(2);
        assertThat(eventCount("wish.paid", requestId)).isOne();
    }

    @Test
    @DisplayName("조르기 예약이 아니거나 · 없는 조르기거나 · 그 조르기의 칸이 아니면 아무것도 하지 않는다")
    void ignoresOthers() {
        Long line = cartLine(1L, 1);
        Long requestId = wishRequestService.send(CHILD, List.of(line), null).requestId();

        for (PurchasedLine other : List.of(
            new PurchasedLine(line, ORDER_ID, null, BUYER_ID, 1L, 1, "other-" + requestId),
            new PurchasedLine(line, ORDER_ID, null, BUYER_ID, 1L, 1, "wish-" + (requestId + 1000)),
            new PurchasedLine(line + 1000, ORDER_ID, null, BUYER_ID, 1L, 1, "wish-" + requestId),
            new PurchasedLine(line, null, null, BUYER_ID, 1L, 1, "wish-" + requestId))) {
            assertThat(wishPurchaseService.onPurchased(other)).isFalse();
        }

        assertThat(status(requestId)).isEqualTo("REQUESTED");
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.wish_purchase", Long.class)).isZero();
    }

    @Test
    @DisplayName("이미 거절 · 철회된 조르기에 늦게 온 구매 사실은 산 것만 남기고(WARN) 상태 · 이벤트는 그대로다. 다시 받아도 1회분")
    void decidedNotPaid(CapturedOutput output) {
        Long rejectedLine = cartLine(1L, 1);
        Long rejected = wishRequestService.send(CHILD, List.of(rejectedLine), null).requestId();
        wishRequestService.reject(GUARDIAN, rejected, "NEXT_TIME");
        Long cancelledLine = cartLine(2L, 1);
        Long cancelled = wishRequestService.send(CHILD, List.of(cancelledLine), null).requestId();
        wishRequestService.cancel(CHILD, cancelled);

        assertThat(wishPurchaseService.onPurchased(purchased(rejectedLine, rejected))).isFalse();
        assertThat(wishPurchaseService.onPurchased(purchased(cancelledLine, cancelled))).isFalse();
        assertThat(wishPurchaseService.onPurchased(purchased(cancelledLine, cancelled))).isFalse();

        assertThat(status(rejected)).isEqualTo("REJECTED");
        assertThat(status(cancelled)).isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForList("select request_id || ':' || cart_item_id || ':' || order_id "
            + "from orders.wish_purchase order by id", String.class))
            .containsExactly(rejected + ":" + rejectedLine + ":" + ORDER_ID,
                cancelled + ":" + cancelledLine + ":" + ORDER_ID);
        assertThat(eventCount("wish.paid", rejected) + eventCount("wish.paid", cancelled)).isZero();
        assertThat(output.getOut()).contains("requestId=" + rejected + " orderId=" + ORDER_ID,
            "requestId=" + cancelled + " orderId=" + ORDER_ID);
    }

    @Test
    @DisplayName("결제 정리로 예약 칸이 빠졌는데 아직 반영 전이면(Kafka 지연) 거절 · 철회가 409이고, 반영되면 PAID")
    void rejectAndCancelWhilePurchaseInProgress() {
        Long bought = cartLine(1L, 1);
        Long left = cartLine(2L, 1);
        Long requestId = wishRequestService.send(CHILD, List.of(bought, left), null).requestId();
        // 결제 정리가 칸을 지운 상태(구매 사실은 아직 조르기에 오지 않음)
        jdbcTemplate.update("delete from orders.cart_item where id = ?", bought);

        assertThatThrownBy(() -> wishRequestService.cancel(CHILD, requestId))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                .isEqualTo(WishErrorCode.WISH_REQUEST_PURCHASE_IN_PROGRESS));
        assertThatThrownBy(() -> wishRequestService.reject(GUARDIAN, requestId, "NEXT_TIME"))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                .isEqualTo(WishErrorCode.WISH_REQUEST_PURCHASE_IN_PROGRESS));
        assertThat(status(requestId)).isEqualTo("REQUESTED");

        assertThat(wishPurchaseService.onPurchased(purchased(bought, requestId))).isTrue();
        assertThat(status(requestId)).isEqualTo("PAID");
        assertThatThrownBy(() -> wishRequestService.cancel(CHILD, requestId))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                .isEqualTo(WishErrorCode.WISH_REQUEST_ALREADY_DECIDED));
    }

    @Test
    @DisplayName("일부 수량 결제 정리가 커밋돼 칸이 남아도(칸 수는 그대로) 반영 전이면 거절 · 철회가 409이고, 반영되면 PAID · wish.paid 1건")
    void rejectAndCancelAfterPartialPurchase() {
        Long line = cartLine(1L, 3);
        Long requestId = wishRequestService.send(CHILD, List.of(line), null).requestId();
        // 결제 정리가 칸에서 1개만 빼고 예약에 결제 정리 표시를 남긴 상태(칸 · 예약은 남음). 구매 사실은 아직 조르기에 오지 않음.
        // 이 컨텍스트는 Kafka가 꺼져 정리 커밋 직후 조르기가 바로 받으므로 정리 결과를 직접 만든다(표시를 남기는 정리는 CartServiceTest)
        jdbcTemplate.update("update orders.cart_item set quantity = 2 where id = ?", line);
        jdbcTemplate.update("update orders.cart_reservation set purchased_at = now() where reservation_id = ?",
            "wish-" + requestId);
        assertThat(jdbcTemplate.queryForList("select id from orders.cart_item where reservation_id = ?", Long.class,
            "wish-" + requestId)).containsExactly(line);

        assertThatThrownBy(() -> wishRequestService.cancel(CHILD, requestId))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                .isEqualTo(WishErrorCode.WISH_REQUEST_PURCHASE_IN_PROGRESS));
        assertThatThrownBy(() -> wishRequestService.reject(GUARDIAN, requestId, "NEXT_TIME"))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                .isEqualTo(WishErrorCode.WISH_REQUEST_PURCHASE_IN_PROGRESS));
        assertThat(status(requestId)).isEqualTo("REQUESTED");

        assertThat(wishPurchaseService.onPurchased(purchased(line, requestId))).isTrue();
        assertThat(status(requestId)).isEqualTo("PAID");
        assertThat(eventCount("wish.paid", requestId)).isOne();
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from orders.cart_reservation where reservation_id = ?", Long.class,
            "wish-" + requestId)).isZero();
    }

    @Test
    @DisplayName("조르기 예약 칸인데 주문 ID 또는 결제 회원이 없으면 건너뛰고 WARN 한 줄(조르기 ID · 칸 ID)")
    void skipsPurchaseWithoutOrderOrPayer(CapturedOutput output) {
        Long line = cartLine(1L, 1);
        Long requestId = wishRequestService.send(CHILD, List.of(line), null).requestId();

        assertThat(wishPurchaseService.onPurchased(
            new PurchasedLine(line, null, null, BUYER_ID, 1L, 1, "wish-" + requestId))).isFalse();
        assertThat(wishPurchaseService.onPurchased(
            new PurchasedLine(line, ORDER_ID, null, null, 1L, 1, "wish-" + requestId))).isFalse();

        assertThat(status(requestId)).isEqualTo("REQUESTED");
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.wish_purchase", Long.class)).isZero();
        String warn = "requestId=" + requestId + " cartItemId=" + line;
        assertThat(output.getOut().split(Pattern.quote(warn), -1)).hasSize(3);
    }

    @Test
    @DisplayName("거절과 구매 사실이 겹치면 하나만 남고 상태 · 산 것 · 이벤트가 그 결과와 맞는다(반복)")
    void rejectAndPurchaseRace() throws InterruptedException {
        for (int round = 1; round <= 5; round++) {
            Long line = cartLine(1L, round);
            Long requestId = wishRequestService.send(CHILD, List.of(line), null).requestId();

            ConcurrentCalls.Outcome outcome = ConcurrentCalls.run(List.of(
                () -> wishRequestService.reject(GUARDIAN, requestId, "NEXT_TIME"),
                () -> wishPurchaseService.onPurchased(purchased(line, requestId))),
                // 구매가 먼저 끝나면 거절은 처리됨 · 결제 반영 중(409)으로 진다
                error -> error instanceof BusinessException e
                    && (e.getErrorCode() == WishErrorCode.WISH_REQUEST_ALREADY_DECIDED
                    || e.getErrorCode() == WishErrorCode.WISH_REQUEST_PURCHASE_IN_PROGRESS));

            assertThat(outcome.unexpected()).as("%d회차 예외", round).isEmpty();
            String status = status(requestId);
            long purchases = jdbcTemplate.queryForObject(
                "select count(*) from orders.wish_purchase where request_id = ?", Long.class, requestId);
            // 거절이 먼저면 구매는 산 것만 남기거나(상태 그대로) 버전 충돌로 진다(Kafka가 다시 주면 산 것만 남김).
            // 구매가 먼저면 거절이 409 또는 버전 충돌로 진다
            assertThat(status).as("%d회차 상태", round).isIn("PAID", "REJECTED");
            assertThat(outcome.conflicts()).as("%d회차 진 쪽", round).isLessThanOrEqualTo(1);
            assertThat(purchases).as("%d회차 산 것", round).isLessThanOrEqualTo(1);
            if ("PAID".equals(status)) {
                assertThat(purchases).as("%d회차 산 것", round).isOne();
            }
            assertThat(eventCount("wish.paid", requestId)).as("%d회차 wish.paid", round)
                .isEqualTo("PAID".equals(status) ? 1 : 0);
            assertThat(eventCount("wish.rejected", requestId)).as("%d회차 wish.rejected", round)
                .isEqualTo("REJECTED".equals(status) ? 1 : 0);
            jdbcTemplate.update("delete from orders.cart_item where id = ?", line);
        }
    }

    @Test
    @DisplayName("칸 전체를 사는 결제 정리와 거절이 겹치면 먼저 커밋한 쪽만 남는다: 결제가 이기면 PAID(거절은 409 · 버전 충돌), "
        + "거절이 이기면 그 칸의 구매는 조르기와 이어지지 않는다(돈은 나갔는데 거절된 조르기가 없다, 반복)")
    void rejectAndFullPurchaseCleanupRace() throws InterruptedException {
        for (int round = 1; round <= 10; round++) {
            Long line = cartLine(1L, round);
            Long requestId = wishRequestService.send(CHILD, List.of(line), null).requestId();

            ConcurrentCalls.Outcome outcome = ConcurrentCalls.run(List.of(
                () -> wishRequestService.reject(GUARDIAN, requestId, "NEXT_TIME"),
                // 결제 정리가 칸 전체를 지운다. 이 컨텍스트는 커밋 뒤 조르기가 바로 받는다
                () -> cartService.clearOrdered(ORDER_ID, List.of(
                    new CartCleanup.OrderedLine(null, line, BUYER_ID, 1L, null, null)))),
                error -> error instanceof BusinessException e
                    && (e.getErrorCode() == WishErrorCode.WISH_REQUEST_ALREADY_DECIDED
                    || e.getErrorCode() == WishErrorCode.WISH_REQUEST_PURCHASE_IN_PROGRESS));

            assertThat(outcome.unexpected()).as("%d회차 예외", round).isEmpty();
            assertThat(outcome.conflicts()).as("%d회차 진 쪽", round).isLessThanOrEqualTo(1);
            String status = status(requestId);
            long purchases = jdbcTemplate.queryForObject(
                "select count(*) from orders.wish_purchase where request_id = ?", Long.class, requestId);
            assertThat(status).as("%d회차 상태", round).isIn("PAID", "REJECTED");
            if ("PAID".equals(status)) {
                assertThat(purchases).as("%d회차 산 것", round).isOne();
                assertThat(eventCount("wish.paid", requestId)).as("%d회차 wish.paid", round).isOne();
                assertThat(eventCount("wish.rejected", requestId)).as("%d회차 wish.rejected", round).isZero();
            } else {
                // 거절이 먼저 커밋됐다: 정리는 예약이 풀린 칸을 지웠거나(구매 사실에 예약 ID 없음) 버전 충돌로 졌다
                assertThat(purchases).as("%d회차 거절된 조르기의 산 것", round).isZero();
                assertThat(eventCount("wish.paid", requestId)).as("%d회차 wish.paid", round).isZero();
                assertThat(eventCount("wish.rejected", requestId)).as("%d회차 wish.rejected", round).isOne();
            }
            assertThat(jdbcTemplate.queryForObject(
                "select count(*) from orders.cart_reservation where reservation_id = ?", Long.class,
                "wish-" + requestId)).as("%d회차 남은 예약", round).isZero();
            jdbcTemplate.update("delete from orders.cart_item where id = ?", line);
        }
    }

    @Test
    @DisplayName("조르기 처리가 실패해도(산 것 저장 실패) 결제 완료 · 장바구니 정리는 그대로이고, 조르기는 대기로 남는다")
    void wishFailureDoesNotUndoPayment() {
        Long line = cartLine(1L, 1);
        Long requestId = wishRequestService.send(CHILD, List.of(line), null).requestId();
        Long orderId = orderService.place(BUYER_ID, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, CHILD_ID, 1, PRICE, line)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시"))).orderId();
        jdbcTemplate.execute("""
            CREATE OR REPLACE FUNCTION orders.test_fail_wish_purchase() RETURNS trigger AS $$
            BEGIN RAISE EXCEPTION 'test failure'; END; $$ LANGUAGE plpgsql""");
        jdbcTemplate.execute("CREATE TRIGGER test_fail_wish_purchase BEFORE INSERT ON orders.wish_purchase "
            + "FOR EACH ROW EXECUTE FUNCTION orders.test_fail_wish_purchase()");
        try {
            eventDelivery.deliver(new StockDeductedEvent(UUID.randomUUID(), LocalDateTime.now(),
                new StockDeductedEvent.Data(orderId)));
            eventDelivery.deliver(PaymentEvents.completed(orderQueries.getOrder(BUYER_ID, orderId), BUYER_ID));
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS test_fail_wish_purchase ON orders.wish_purchase");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS orders.test_fail_wish_purchase()");
        }

        assertThat(orderQueries.getOrder(BUYER_ID, orderId).status()).isEqualTo("PAID");
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.cart_item where id = ?", Long.class, line))
            .isZero();
        assertThat(status(requestId)).isEqualTo("REQUESTED");
        assertThat(eventCount("wish.paid", requestId)).isZero();
    }

    private PurchasedLine purchased(Long cartItemId, Long requestId) {
        return new PurchasedLine(cartItemId, ORDER_ID, null, BUYER_ID, 1L, 1, "wish-" + requestId);
    }

    private Long cartLine(Long optionId, int quantity) {
        return cartService.addItem(CartOwners.child(CHILD_ID, BUYER_ID), optionId, null, quantity).line().cartItemId();
    }

    private String status(Long requestId) {
        return jdbcTemplate.queryForObject("select status from orders.wish_request where id = ?", String.class,
            requestId);
    }

    private long eventCount(String eventType, Long requestId) {
        return jdbcTemplate.queryForObject("select count(*) from orders.order_outbox where event_type = ? "
            + "and payload::jsonb ->> 'aggregateId' = ?", Long.class, eventType, String.valueOf(requestId));
    }
}
