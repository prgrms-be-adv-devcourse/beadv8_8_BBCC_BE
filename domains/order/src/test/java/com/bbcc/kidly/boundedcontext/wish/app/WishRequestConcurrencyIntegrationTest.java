package com.bbcc.kidly.boundedcontext.wish.app;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.cart.CartOwners;
import com.bbcc.kidly.boundedcontext.cart.app.CartErrorCode;
import com.bbcc.kidly.boundedcontext.cart.app.CartService;
import com.bbcc.kidly.boundedcontext.order.ConcurrentCalls;
import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import com.bbcc.kidly.boundedcontext.wish.app.dto.WishRequestView;
import com.bbcc.kidly.boundedcontext.wish.out.actor.ChildWishActor;
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
 * 조르기 동시 처리(#999): 같은 칸을 동시에 두 조르기로 보내도 하나만 들어가고(장바구니 칸 @Version · 예약), 같은 조르기의 거절 · 철회가
 * 겹쳐도 한 번만 처리된다(조르기 @Version). 진 쪽은 조르기 이벤트도 남기지 않는다. 회차마다 새 칸 · 새 조르기로 되풀이한다.
 */
@IntegrationTest
class WishRequestConcurrencyIntegrationTest {

    private static final int ROUNDS = 10;
    private static final Long CHILD_ID = 1L;
    private static final ChildWishActor CHILD = new ChildWishActor(CHILD_ID, BUYER_ID);
    private static final ParentWishActor GUARDIAN = new ParentWishActor(BUYER_ID);

    @Autowired
    private WishRequestService wishRequestService;

    @Autowired
    private CartService cartService;

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
    @DisplayName("같은 칸을 동시에 두 번 보내면 하나만 들어가고 칸 예약도 그 조르기다(반복)")
    void sendSameLineTwice() throws InterruptedException {
        for (int round = 1; round <= ROUNDS; round++) {
            Long line = newLine(round);
            List<WishRequestView> results = new CopyOnWriteArrayList<>();
            Runnable send = () -> results.add(wishRequestService.send(CHILD, List.of(line), null));

            // 늦게 읽은 쪽은 버전 충돌이 아니라 진행 중 칸(409) · 예약된 칸(409)으로 진다. 모두 "하나만"의 정상 결과다
            ConcurrentCalls.Outcome outcome = ConcurrentCalls.run(List.of(send, send),
                error -> error instanceof BusinessException e
                    && (e.getErrorCode() == WishErrorCode.WISH_ITEM_IN_REQUEST
                    || e.getErrorCode() == CartErrorCode.CART_ITEM_RESERVED));

            assertThat(outcome.unexpected()).as("%d회차 예외", round).isEmpty();
            assertThat(outcome.conflicts()).as("%d회차 진 쪽", round).isEqualTo(1);
            assertThat(results).as("%d회차 조르기", round).hasSize(1);
            assertThat(jdbcTemplate.queryForObject(
                "select count(*) from orders.wish_request_line where cart_item_id = ?", Long.class, line))
                .as("%d회차 칸이 든 조르기 수", round).isOne();
            assertThat(jdbcTemplate.queryForObject("select reservation_id from orders.cart_item where id = ?",
                String.class, line)).as("%d회차 예약", round).isEqualTo("wish-" + results.getFirst().requestId());
            assertThat(eventCount("wish.sent", results.getFirst().requestId())).as("%d회차 이벤트", round).isOne();
        }
    }

    @Test
    @DisplayName("같은 조르기의 거절과 철회가 겹치면 하나만 처리되고, 진 쪽은 409 · 이벤트 없음(반복)")
    void rejectAndCancel() throws InterruptedException {
        for (int round = 1; round <= ROUNDS; round++) {
            Long requestId = wishRequestService.send(CHILD, List.of(newLine(round)), null).requestId();
            List<WishRequestView> results = new CopyOnWriteArrayList<>();

            ConcurrentCalls.Outcome outcome = ConcurrentCalls.run(List.of(
                () -> results.add(wishRequestService.reject(GUARDIAN, requestId, "NEXT_TIME")),
                () -> results.add(wishRequestService.cancel(CHILD, requestId))),
                error -> error instanceof BusinessException e
                    && e.getErrorCode() == WishErrorCode.WISH_REQUEST_ALREADY_DECIDED);

            assertThat(outcome.unexpected()).as("%d회차 예외", round).isEmpty();
            assertThat(outcome.conflicts()).as("%d회차 진 쪽", round).isEqualTo(1);
            assertThat(results).as("%d회차 처리", round).hasSize(1);
            String status = jdbcTemplate.queryForObject("select status from orders.wish_request where id = ?",
                String.class, requestId);
            assertThat(status).as("%d회차 상태", round).isEqualTo(results.getFirst().status());
            assertThat(eventCount("wish.rejected", requestId) + eventCount("wish.cancelled", requestId))
                .as("%d회차 처리 이벤트", round).isOne();
            assertThat(jdbcTemplate.queryForObject("select count(*) from orders.cart_reservation where reservation_id"
                + " = ?", Long.class, "wish-" + requestId)).as("%d회차 예약 해제", round).isZero();
        }
    }

    private Long newLine(int round) {
        Long optionId = 100L + round;
        productReplicaRepository.save(ReplicaProduct.create(
            ReplicaFixture.product(optionId, 25_000L, "ON_SALE", false, OCCURRED_AT)));
        return cartService.addItem(CartOwners.child(CHILD_ID, BUYER_ID), optionId, null, 1).line().cartItemId();
    }

    // 조르기 이벤트의 대상(aggregateId)은 조르기 ID다. 진 쪽은 롤백돼 행이 없다
    private long eventCount(String eventType, Long requestId) {
        return jdbcTemplate.queryForObject("select count(*) from orders.order_outbox where event_type = ? "
            + "and payload::jsonb ->> 'aggregateId' = ?", Long.class, eventType, String.valueOf(requestId));
    }
}
