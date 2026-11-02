package com.bbcc.kidly.boundedcontext.wish.app;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.ConcurrentCalls;
import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import com.bbcc.kidly.boundedcontext.wish.app.dto.ApproveWishCommand;
import com.bbcc.kidly.boundedcontext.wish.app.dto.WishRequestView;
import com.bbcc.kidly.boundedcontext.wish.out.actor.ParentWishActor;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 조르기 동시 처리(#701): 같은 조르기의 승인 · 거절이 겹쳐도 한 번만 처리되고(조르기 @Version), 바로 결제 주문도 하나만 생긴다.
 * 같은 줄을 동시에 다른 조르기에 넣어도 하나만 들어간다(줄 @Version). 진 쪽은 조르기 이벤트(#702)도 남기지 않는다.
 * 회차마다 새 상품 줄 · 새 조르기로 되풀이한다.
 */
@IntegrationTest
class WishRequestConcurrencyIntegrationTest {

    private static final int ROUNDS = 10;
    private static final Long CHILD_ID = 1L;
    private static final long PRICE = 25_000L;
    private static final ParentWishActor PARENT = new ParentWishActor(BUYER_ID);
    private static final TestChildWishActor CHILD = new TestChildWishActor(CHILD_ID);
    private static final ApproveWishCommand.ShippingAddress ADDRESS =
        new ApproveWishCommand.ShippingAddress("보호자", "010-0000-0000", "12345", "서울시");

    @Autowired
    private WishService wishService;

    @Autowired
    private WishRequestService wishRequestService;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private ChildReplicaRepository childReplicaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        childReplicaRepository.save(ReplicaChild.create(ReplicaFixture.child("115.0", false, OCCURRED_AT)));
    }

    @Test
    @DisplayName("같은 조르기를 동시에 두 번 바로 결제로 승인하면 한 번만 승인되고 주문도 하나다(반복)")
    void approveTwice() throws InterruptedException {
        for (int round = 1; round <= ROUNDS; round++) {
            Long wishItemId = newWishItem(round);
            Long requestId = wishRequestService.send(CHILD, CHILD_ID, List.of(wishItemId), null).requestId();
            long ordersBefore = orderCount();
            List<WishRequestView> results = new CopyOnWriteArrayList<>();

            ConcurrentCalls.Outcome outcome = ConcurrentCalls.run(List.of(
                () -> results.add(approveOrder(requestId, wishItemId)),
                () -> results.add(approveOrder(requestId, wishItemId))),
                WishRequestConcurrencyIntegrationTest::isAlreadyDecided);

            assertThat(outcome.unexpected()).as("%d회차 예외", round).isEmpty();
            assertThat(outcome.conflicts()).as("%d회차 진 쪽", round).isEqualTo(1);
            assertThat(results).as("%d회차 승인", round).hasSize(1);
            assertThat(orderCount() - ordersBefore).as("%d회차 새 주문 수", round).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("select order_id from orders.wish_request where id = ?", Long.class,
                requestId)).as("%d회차 기록한 주문", round).isEqualTo(results.getFirst().orderId());
            assertThat(wishEventCount("wish.approved", requestId)).as("%d회차 승인 이벤트(#702)", round).isOne();
        }
    }

    @Test
    @DisplayName("같은 조르기의 승인(장바구니로 옮기기)과 거절이 겹치면 하나만 처리되고, 장바구니도 그 결과와 맞는다(반복)")
    void approveAndReject() throws InterruptedException {
        for (int round = 1; round <= ROUNDS; round++) {
            Long wishItemId = newWishItem(round);
            Long requestId = wishRequestService.send(CHILD, CHILD_ID, List.of(wishItemId), null).requestId();
            long cartLinesBefore = cartLineCount();
            List<WishRequestView> results = new CopyOnWriteArrayList<>();

            ConcurrentCalls.Outcome outcome = ConcurrentCalls.run(List.of(
                () -> results.add(wishRequestService.approve(PARENT, requestId, new ApproveWishCommand("CART",
                    List.of(new ApproveWishCommand.Item(wishItemId, 1, null)), null))),
                () -> results.add(wishRequestService.reject(PARENT, requestId, "NEXT_TIME"))),
                WishRequestConcurrencyIntegrationTest::isAlreadyDecided);

            assertThat(outcome.unexpected()).as("%d회차 예외", round).isEmpty();
            assertThat(outcome.conflicts()).as("%d회차 진 쪽", round).isEqualTo(1);
            assertThat(results).as("%d회차 처리", round).hasSize(1);
            String status = jdbcTemplate.queryForObject("select status from orders.wish_request where id = ?",
                String.class, requestId);
            assertThat(status).as("%d회차 상태", round).isEqualTo(results.getFirst().status());
            assertThat(cartLineCount() - cartLinesBefore).as("%d회차 장바구니 줄", round)
                .isEqualTo("APPROVED".equals(status) ? 1 : 0);
            assertThat(wishEventCount("wish.approved", requestId) + wishEventCount("wish.rejected", requestId))
                .as("%d회차 처리 이벤트(#702)", round).isOne();
        }
    }

    @Test
    @DisplayName("같은 줄을 동시에 두 조르기로 보내면 하나만 만들어지고 줄은 그 조르기로 잠긴다(반복)")
    void sendSameItemTwice() throws InterruptedException {
        for (int round = 1; round <= ROUNDS; round++) {
            Long wishItemId = newWishItem(round);
            List<WishRequestView> results = new CopyOnWriteArrayList<>();
            Runnable send = () -> results.add(wishRequestService.send(CHILD, CHILD_ID, List.of(wishItemId), null));

            ConcurrentCalls.Outcome outcome = ConcurrentCalls.run(List.of(send, send),
                error -> error instanceof BusinessException e
                    && e.getErrorCode() == WishErrorCode.WISH_ITEM_IN_REQUEST);

            assertThat(outcome.unexpected()).as("%d회차 예외", round).isEmpty();
            assertThat(outcome.conflicts()).as("%d회차 진 쪽", round).isEqualTo(1);
            assertThat(results).as("%d회차 조르기", round).hasSize(1);
            assertThat(jdbcTemplate.queryForObject(
                "select count(*) from orders.wish_request_line where wish_item_id = ?", Long.class, wishItemId))
                .as("%d회차 줄이 든 조르기 수", round).isOne();
            assertThat(jdbcTemplate.queryForObject("select request_id from orders.wish_item where id = ?", Long.class,
                wishItemId)).as("%d회차 잠금", round).isEqualTo(results.getFirst().requestId());
        }
    }

    // 늦게 읽은 쪽은 버전 충돌이 아니라 이미 처리됨(409)으로 진다. 둘 다 "한 번만 처리"의 정상 결과다
    private static boolean isAlreadyDecided(Throwable error) {
        return error instanceof BusinessException e && e.getErrorCode() == WishErrorCode.WISH_REQUEST_ALREADY_DECIDED;
    }

    private WishRequestView approveOrder(Long requestId, Long wishItemId) {
        return wishRequestService.approve(PARENT, requestId, new ApproveWishCommand("ORDER",
            List.of(new ApproveWishCommand.Item(wishItemId, 1, PRICE)), ADDRESS));
    }

    private Long newWishItem(int round) {
        Long optionId = 100L + round;
        productReplicaRepository.save(ReplicaProduct.create(
            ReplicaFixture.product(optionId, PRICE, "ON_SALE", false, OCCURRED_AT)));
        return wishService.add(CHILD, CHILD_ID, optionId, 1).line().wishItemId();
    }

    // 조르기 이벤트의 대상(aggregateId)은 조르기 ID다. 진 쪽은 롤백돼 행이 없다
    private long wishEventCount(String eventType, Long requestId) {
        return jdbcTemplate.queryForObject("select count(*) from orders.order_outbox where event_type = ? "
            + "and payload::jsonb ->> 'aggregateId' = ?", Long.class, eventType, String.valueOf(requestId));
    }

    private long orderCount() {
        return jdbcTemplate.queryForObject("select count(*) from orders.orders where member_id = ?", Long.class,
            BUYER_ID);
    }

    private long cartLineCount() {
        return jdbcTemplate.queryForObject("select count(*) from orders.cart_item where member_id = ?", Long.class,
            BUYER_ID);
    }
}
