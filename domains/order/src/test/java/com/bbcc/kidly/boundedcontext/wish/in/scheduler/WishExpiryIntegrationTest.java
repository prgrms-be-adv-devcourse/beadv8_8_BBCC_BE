package com.bbcc.kidly.boundedcontext.wish.in.scheduler;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

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
import com.bbcc.kidly.boundedcontext.wish.app.WishErrorCode;
import com.bbcc.kidly.boundedcontext.wish.app.WishExpiryService;
import com.bbcc.kidly.boundedcontext.wish.app.WishPurchaseService;
import com.bbcc.kidly.boundedcontext.wish.app.WishPurchaseService.PurchasedLine;
import com.bbcc.kidly.boundedcontext.wish.app.WishRequestService;
import com.bbcc.kidly.boundedcontext.wish.app.dto.WishRequestView;
import com.bbcc.kidly.boundedcontext.wish.app.dto.WishTimelineView;
import com.bbcc.kidly.boundedcontext.wish.out.actor.ChildWishActor;
import com.bbcc.kidly.boundedcontext.wish.out.actor.ParentWishActor;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 조르기 기한 만료(#1008, BEG-05): 테스트 설정의 기한은 기본(7일)이다. 보낸 시각을 기한보다 앞으로 옮겨(DB 직접) 기한이 지난 것을 만든다.
 * 주기 작업은 테스트가 직접 부른다: 주문 모듈 테스트 설정(application-test.yml)의 주기가 아주 길고(P3650D) 처음 실행도 한 주기 뒤라
 * (initialDelay) 저절로 돌지 않는다.
 */
@IntegrationTest
class WishExpiryIntegrationTest {

    private static final Long CHILD_ID = 1L;
    private static final ChildWishActor CHILD = new ChildWishActor(CHILD_ID, BUYER_ID);
    private static final ParentWishActor GUARDIAN = new ParentWishActor(BUYER_ID);

    @Autowired
    private WishExpiryScheduler scheduler;

    @Autowired
    private WishExpiryService wishExpiryService;

    @Autowired
    private WishRequestService wishRequestService;

    @Autowired
    private WishPurchaseService wishPurchaseService;

    @Autowired
    private CartService cartService;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private ChildReplicaRepository childReplicaRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderQueryService orderQueries;

    @Autowired
    private OrderEventDelivery eventDelivery;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        for (long optionId = 1; optionId <= 12; optionId++) {
            productReplicaRepository.save(ReplicaProduct.create(
                ReplicaFixture.product(optionId, 25_000L, "ON_SALE", false, OCCURRED_AT)));
        }
        childReplicaRepository.save(ReplicaChild.create(ReplicaFixture.child("115.0", false, OCCURRED_AT)));
    }

    @Test
    @DisplayName("기한이 지난 대기 조르기만 주기 작업이 EXPIRED로 바꾸고 예약을 풀고 wish.expired를 낸다. 다시 돌아도 1회분")
    void expiresOverdue() {
        Long line = line(1L);
        Long overdue = send(line);
        Long fresh = send(line(2L));
        sentDaysAgo(overdue, 8);
        sentDaysAgo(fresh, 6);

        scheduler.expireOverdue();
        scheduler.expireOverdue();

        assertThat(status(overdue)).isEqualTo("EXPIRED");
        assertThat(jdbcTemplate.queryForObject("select expired_at from orders.wish_request where id = ?",
            LocalDateTime.class, overdue)).isNotNull();
        assertThat(status(fresh)).isEqualTo("REQUESTED");
        assertThat(eventCount("wish.expired", overdue)).isOne();
        assertThat(eventCount("wish.expired", fresh)).isZero();
        assertThat(jdbcTemplate.queryForObject("select reservation_id from orders.cart_item where id = ?",
            String.class, line)).isNull();
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.cart_reservation where reservation_id = ?",
            Long.class, "wish-" + overdue)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.cart_item where id = ?", Long.class, line))
            .as("칸은 장바구니에 남는다").isOne();
        // 풀린 칸으로 다시 조를 수 있다
        assertThat(wishRequestService.send(CHILD, List.of(line), null).status()).isEqualTo("REQUESTED");
    }

    @Test
    @DisplayName("결제 정리로 예약 칸이 빠졌는데 구매 사실이 아직 오지 않았으면(결제 반영 중) 기한이 지나도 만료하지 않는다(대기 · 이벤트 0건)")
    void skipsWhilePurchaseInProgress() {
        Long bought = line(1L);
        Long requestId = wishRequestService.send(CHILD, List.of(bought, line(2L)), null).requestId();
        sentDaysAgo(requestId, 8);
        // 결제 정리가 칸 하나를 지운 상태
        jdbcTemplate.update("delete from orders.cart_item where id = ?", bought);

        scheduler.expireOverdue();

        assertThat(wishExpiryService.expire(requestId, LocalDateTime.now())).isFalse();
        assertThat(status(requestId)).isEqualTo("REQUESTED");
        assertThat(eventCount("wish.expired", requestId)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.cart_reservation where reservation_id = ?",
            Long.class, "wish-" + requestId)).as("예약은 그대로").isOne();
    }

    @Test
    @DisplayName("칸이 남는 일부 수량 결제로 예약에 결제 정리 표시가 있으면 기한이 지나도 만료하지 않고 예약을 유지한다. 구매 사실이 오면 "
        + "PAID이고 그 뒤에도 만료 대상이 아니다")
    void skipsPartialPurchaseMarked() {
        Long line = cartService.addItem(CartOwners.child(CHILD_ID, BUYER_ID), 1L, null, 3).line().cartItemId();
        Long requestId = send(line);
        sentDaysAgo(requestId, 8);
        // 결제 정리가 칸에서 1개만 빼고 예약에 표시를 남긴 상태(칸 수는 그대로). 이 컨텍스트는 정리 커밋 직후 조르기가 바로 받으므로
        // 정리 결과를 직접 만든다
        jdbcTemplate.update("update orders.cart_item set quantity = 2 where id = ?", line);
        jdbcTemplate.update("update orders.cart_reservation set purchased_at = now() where reservation_id = ?",
            "wish-" + requestId);

        scheduler.expireOverdue();

        assertThat(wishExpiryService.expire(requestId, LocalDateTime.now())).isFalse();
        assertThat(status(requestId)).isEqualTo("REQUESTED");
        assertThat(eventCount("wish.expired", requestId)).isZero();
        assertThat(jdbcTemplate.queryForObject("select reservation_id from orders.cart_item where id = ?",
            String.class, line)).as("예약은 그대로").isEqualTo("wish-" + requestId);

        assertThat(wishPurchaseService.onPurchased(purchased(line, requestId))).isTrue();
        assertThat(status(requestId)).isEqualTo("PAID");
        assertThat(wishExpiryService.expire(requestId, LocalDateTime.now())).isFalse();
        assertThat(eventCount("wish.expired", requestId)).isZero();
    }

    @Test
    @DisplayName("기한 지난 조르기의 만료와 칸 전체를 사는 결제 정리가 겹치면 먼저 커밋한 쪽만 남는다: 만료가 이기면 그 칸의 구매는 조르기와 "
        + "이어지지 않고, 결제가 이기면 PAID(만료는 예약 버전 충돌로 짐)(반복)")
    void fullPurchaseCleanupAndExpiryRace() throws InterruptedException {
        for (int round = 1; round <= 5; round++) {
            Long optionId = 7L + round;
            Long line = line(optionId);
            Long requestId = send(line);
            sentDaysAgo(requestId, 8);
            LocalDateTime now = LocalDateTime.now();

            ConcurrentCalls.Outcome outcome = ConcurrentCalls.run(List.of(
                () -> wishExpiryService.expire(requestId, now),
                () -> cartService.clearOrdered(900L, List.of(
                    new CartCleanup.OrderedLine(null, line, BUYER_ID, optionId, null, null)))));

            assertThat(outcome.unexpected()).as("%d회차 예외", round).isEmpty();
            assertThat(outcome.conflicts()).as("%d회차 진 쪽", round).isLessThanOrEqualTo(1);
            String status = status(requestId);
            long purchases = jdbcTemplate.queryForObject(
                "select count(*) from orders.wish_purchase where request_id = ?", Long.class, requestId);
            assertThat(status).as("%d회차 상태", round).isIn("PAID", "EXPIRED");
            if ("PAID".equals(status)) {
                assertThat(purchases).as("%d회차 산 것", round).isOne();
                assertThat(eventCount("wish.paid", requestId)).as("%d회차 wish.paid", round).isOne();
                assertThat(eventCount("wish.expired", requestId)).as("%d회차 wish.expired", round).isZero();
            } else {
                assertThat(purchases).as("%d회차 만료된 조르기의 산 것", round).isZero();
                assertThat(eventCount("wish.paid", requestId)).as("%d회차 wish.paid", round).isZero();
                assertThat(eventCount("wish.expired", requestId)).as("%d회차 wish.expired", round).isOne();
            }
            jdbcTemplate.update("delete from orders.cart_item where id = ?", line);
        }
    }

    @Test
    @DisplayName("유예(기본 15분): 기한이 지나도 유예 안이면 만료 작업은 만료하지 않고(거절 · 철회 409 판정은 그대로), 유예가 지나면 만료한다")
    void graceBeforeExpiry() {
        Long line = line(1L);
        Long requestId = send(line);
        sentAgo(requestId, Duration.ofDays(7).plusMinutes(5));

        scheduler.expireOverdue();

        assertThat(wishExpiryService.expire(requestId, LocalDateTime.now())).isFalse();
        assertThat(status(requestId)).isEqualTo("REQUESTED");
        assertThat(eventCount("wish.expired", requestId)).isZero();
        assertThat(jdbcTemplate.queryForObject("select reservation_id from orders.cart_item where id = ?",
            String.class, line)).as("예약은 그대로").isEqualTo("wish-" + requestId);
        assertExpired(() -> wishRequestService.cancel(CHILD, requestId));

        sentAgo(requestId, Duration.ofDays(7).plusMinutes(20));
        scheduler.expireOverdue();

        assertThat(status(requestId)).isEqualTo("EXPIRED");
        assertThat(eventCount("wish.expired", requestId)).isOne();
    }

    @Test
    @DisplayName("기한 전에 만든 주문의 결제 완료가 기한 뒤 유예 안에 오면 만료 작업이 돌아도 PAID(산 것 · wish.paid 1건)")
    void orderBeforeDeadlinePaidWithinGrace() {
        Long line = line(1L);
        Long requestId = send(line);
        Long orderId = orderService.place(BUYER_ID, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, CHILD_ID, 1, 25_000L, line)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시"))).orderId();
        // 주문 뒤 기한이 지났다(유예 안). 결제 마감은 주문 시각 기준이라 아직 결제할 수 있다
        sentAgo(requestId, Duration.ofDays(7).plusMinutes(5));
        scheduler.expireOverdue();
        assertThat(status(requestId)).isEqualTo("REQUESTED");

        eventDelivery.deliver(new StockDeductedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new StockDeductedEvent.Data(orderId)));
        eventDelivery.deliver(PaymentEvents.completed(orderQueries.getOrder(BUYER_ID, orderId), BUYER_ID));

        assertThat(orderQueries.getOrder(BUYER_ID, orderId).status()).isEqualTo("PAID");
        assertThat(status(requestId)).isEqualTo("PAID");
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.wish_purchase where request_id = ?",
            Long.class, requestId)).isOne();
        assertThat(eventCount("wish.paid", requestId)).isOne();
        scheduler.expireOverdue();
        assertThat(eventCount("wish.expired", requestId)).isZero();
    }

    @Test
    @DisplayName("같은 조르기를 동시에 두 번 만료해도 한 번만(이벤트 1건)")
    void concurrentExpireOnce() throws InterruptedException {
        Long requestId = send(line(1L));
        sentDaysAgo(requestId, 8);
        LocalDateTime now = LocalDateTime.now();

        ConcurrentCalls.Outcome outcome = ConcurrentCalls.run(List.of(
            () -> wishExpiryService.expire(requestId, now), () -> wishExpiryService.expire(requestId, now)));

        assertThat(outcome.unexpected()).isEmpty();
        assertThat(status(requestId)).isEqualTo("EXPIRED");
        assertThat(eventCount("wish.expired", requestId)).isOne();
    }

    @Test
    @DisplayName("응답의 expiresAt = 보낸 시각 + 7일(대기 중일 때만), 만료되면 null이고 expiredAt · 타임라인 EXPIRED")
    void expiresAtAndTimeline() {
        Long requestId = send(line(1L));
        LocalDateTime sentAt = jdbcTemplate.queryForObject("select created_at from orders.wish_request where id = ?",
            LocalDateTime.class, requestId);

        WishRequestView waiting = wishRequestService.getRequest(CHILD, requestId);
        assertThat(waiting.expiresAt()).isEqualTo(sentAt.plusDays(7));
        assertThat(waiting.expiredAt()).isNull();

        sentDaysAgo(requestId, 8);
        scheduler.expireOverdue();

        WishRequestView expired = wishRequestService.getRequest(GUARDIAN, requestId);
        assertThat(expired.status()).isEqualTo("EXPIRED");
        assertThat(expired.expiresAt()).isNull();
        assertThat(expired.expiredAt()).isNotNull();
        WishTimelineView timeline = wishRequestService.getTimeline(GUARDIAN, requestId);
        assertThat(timeline.steps()).extracting(WishTimelineView.Step::step, WishTimelineView.Step::status)
            .containsExactly(tuple("SENT", "DONE"), tuple("EXPIRED", "DONE"));
        assertThat(timeline.steps().get(1).at()).isEqualTo(expired.expiredAt());
    }

    @Test
    @DisplayName("기한이 지난 조르기의 거절 · 철회는 주기 작업 전이라도 409(WISH_REQUEST_EXPIRED)이고, 만료 뒤에도 같은 409")
    void rejectAndCancelOverdue() {
        Long requestId = send(line(1L));
        sentDaysAgo(requestId, 8);

        assertExpired(() -> wishRequestService.cancel(CHILD, requestId));
        assertExpired(() -> wishRequestService.reject(GUARDIAN, requestId, "NEXT_TIME"));
        assertThat(status(requestId)).isEqualTo("REQUESTED");

        scheduler.expireOverdue();

        assertExpired(() -> wishRequestService.cancel(CHILD, requestId));
        assertExpired(() -> wishRequestService.reject(GUARDIAN, requestId, "NEXT_TIME"));
    }

    @Test
    @DisplayName("결제 완료가 먼저면 만료하지 않고, 만료가 먼저면 늦게 온 구매 사실은 산 것만 남긴다(상태 · 이벤트 그대로)")
    void paymentAndExpirySequential() {
        Long paidLine = line(1L);
        Long paid = send(paidLine);
        wishPurchaseService.onPurchased(purchased(paidLine, paid));
        sentDaysAgo(paid, 8);
        Long expiredLine = line(2L);
        Long expired = send(expiredLine);
        sentDaysAgo(expired, 8);

        scheduler.expireOverdue();
        assertThat(wishPurchaseService.onPurchased(purchased(expiredLine, expired))).isFalse();

        assertThat(status(paid)).isEqualTo("PAID");
        assertThat(eventCount("wish.expired", paid)).isZero();
        assertThat(status(expired)).isEqualTo("EXPIRED");
        assertThat(eventCount("wish.paid", expired)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.wish_purchase where request_id = ?",
            Long.class, expired)).isOne();
    }

    @Test
    @DisplayName("만료와 결제 완료가 겹치면 먼저 저장한 쪽만 남고 상태 · 이벤트가 그 결과와 맞는다(반복)")
    void paymentAndExpiryRace() throws InterruptedException {
        for (int round = 1; round <= 5; round++) {
            Long line = line(2L + round);
            Long requestId = send(line);
            sentDaysAgo(requestId, 8);
            LocalDateTime now = LocalDateTime.now();

            ConcurrentCalls.Outcome outcome = ConcurrentCalls.run(List.of(
                () -> wishExpiryService.expire(requestId, now),
                () -> wishPurchaseService.onPurchased(purchased(line, requestId))));

            assertThat(outcome.unexpected()).as("%d회차 예외", round).isEmpty();
            String status = status(requestId);
            assertThat(status).as("%d회차 상태", round).isIn("PAID", "EXPIRED");
            assertThat(eventCount("wish.paid", requestId)).as("%d회차 wish.paid", round)
                .isEqualTo("PAID".equals(status) ? 1 : 0);
            assertThat(eventCount("wish.expired", requestId)).as("%d회차 wish.expired", round)
                .isEqualTo("EXPIRED".equals(status) ? 1 : 0);
        }
    }

    private static void assertExpired(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(WishErrorCode.WISH_REQUEST_EXPIRED));
    }

    private PurchasedLine purchased(Long cartItemId, Long requestId) {
        return new PurchasedLine(cartItemId, 900L, null, BUYER_ID, 1L, 1, "wish-" + requestId);
    }

    private Long send(Long cartItemId) {
        return wishRequestService.send(CHILD, List.of(cartItemId), null).requestId();
    }

    private Long line(Long optionId) {
        return cartService.addItem(CartOwners.child(CHILD_ID, BUYER_ID), optionId, null, 1).line().cartItemId();
    }

    private void sentAgo(Long requestId, Duration ago) {
        jdbcTemplate.update("update orders.wish_request set created_at = ? where id = ?",
            LocalDateTime.now().minus(ago).truncatedTo(ChronoUnit.MICROS), requestId);
    }

    private void sentDaysAgo(Long requestId, int days) {
        jdbcTemplate.update("update orders.wish_request set created_at = ? where id = ?",
            LocalDateTime.now().minusDays(days).truncatedTo(ChronoUnit.MICROS), requestId);
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
