package com.bbcc.kidly.boundedcontext.wish.app;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.cart.CartOwners;
import com.bbcc.kidly.boundedcontext.cart.app.CartService;
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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 조르기 이벤트(#702 · #999)가 업무와 같은 트랜잭션에서 주문 아웃박스(kidly.order.events)에 남는다: 보내기 wish.sent · 결제 완료
 * wish.paid · 거절 wish.rejected · 철회 wish.cancelled. 키는 자녀 프로필 ID, 대상(aggregateId)은 조르기 ID다.
 * 실패한 업무는 이벤트를 남기지 않는다. wish.approved · wish.item-added는 더 내지 않는다.
 */
@IntegrationTest
class WishEventsOutboxIntegrationTest {

    private static final Long CHILD_ID = 1L;
    private static final ChildWishActor CHILD = new ChildWishActor(CHILD_ID, BUYER_ID);
    private static final ParentWishActor GUARDIAN = new ParentWishActor(BUYER_ID);

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        for (long optionId = 1; optionId <= 3; optionId++) {
            productReplicaRepository.save(ReplicaProduct.create(
                ReplicaFixture.product(optionId, 25_000L, "ON_SALE", false, OCCURRED_AT)));
        }
        childReplicaRepository.save(ReplicaChild.create(ReplicaFixture.child("115.0", false, OCCURRED_AT)));
    }

    @Test
    @DisplayName("보내기 · 결제 완료 · 거절 · 철회가 키 = 자녀 프로필 ID로 아웃박스에 남는다(사실만, 문구 · 개인정보 없음)")
    void eventsInOutbox() {
        Long paidLine = line(1L, 2);
        Long paid = wishRequestService.send(CHILD, List.of(paidLine), "사 주세요").requestId();
        wishPurchaseService.onPurchased(new PurchasedLine(paidLine, 77L, null, BUYER_ID, 1L, 2, "wish-" + paid));
        Long rejected = wishRequestService.send(CHILD, List.of(line(2L, 1)), null).requestId();
        wishRequestService.reject(GUARDIAN, rejected, "TOO_EXPENSIVE");
        Long cancelled = wishRequestService.send(CHILD, List.of(line(3L, 1)), null).requestId();
        wishRequestService.cancel(CHILD, cancelled);

        assertThat(eventTypes()).containsExactly("cart.item-added", "wish.sent", "wish.paid", "cart.item-added",
            "wish.sent", "wish.rejected", "cart.item-added", "wish.sent", "wish.cancelled");
        JsonNode sent = payload("wish.sent", paid);
        assertThat(sent.get("correlationId").asString()).isEqualTo(String.valueOf(CHILD_ID));
        assertThat(sent.get("producer").asString()).isEqualTo("order");
        assertThat(sent.get("data").get("childId").asLong()).isEqualTo(CHILD_ID);
        assertThat(sent.get("data").get("memberId").asLong()).isEqualTo(BUYER_ID);
        assertThat(sent.get("data").get("items").get(0).get("optionId").asLong()).isEqualTo(1L);
        assertThat(sent.get("data").get("items").get(0).get("quantity").asInt()).isEqualTo(2);
        assertThat(sent.toString()).doesNotContain("사 주세요");
        JsonNode paidData = payload("wish.paid", paid).get("data");
        assertThat(paidData.get("orderId").asLong()).isEqualTo(77L);
        assertThat(paidData.get("payerType").asString()).isEqualTo("MEMBER");
        assertThat(paidData.get("payerId").asLong()).isEqualTo(BUYER_ID);
        assertThat(payload("wish.rejected", rejected).get("data").get("reason").asString()).isEqualTo("TOO_EXPENSIVE");
        assertThat(payload("wish.cancelled", cancelled).get("data").get("cancelReason").asString())
            .isEqualTo("DIRECT");
    }

    @Test
    @DisplayName("실패한 보내기 · 거절은 이벤트를 남기지 않는다")
    void failedLeavesNoEvent() {
        Long line = line(1L, 1);
        Long requestId = wishRequestService.send(CHILD, List.of(line), null).requestId();
        jdbcTemplate.update("delete from orders.order_outbox");

        assertThatThrownBy(() -> wishRequestService.send(CHILD, List.of(line), null))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> wishRequestService.reject(GUARDIAN, requestId, "WRONG"))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> wishRequestService.reject(CHILD, requestId, "OTHER"))
            .isInstanceOf(BusinessException.class);

        assertThat(eventTypes()).isEmpty();
    }

    private Long line(Long optionId, int quantity) {
        return cartService.addItem(CartOwners.child(CHILD_ID, BUYER_ID), optionId, null, quantity).line().cartItemId();
    }

    private List<String> eventTypes() {
        return jdbcTemplate.queryForList("select event_type from orders.order_outbox order by id", String.class);
    }

    private JsonNode payload(String eventType, Long requestId) {
        return jsonMapper.readTree(jdbcTemplate.queryForObject("select payload from orders.order_outbox where "
            + "event_type = ? and payload::jsonb ->> 'aggregateId' = ?", String.class, eventType,
            String.valueOf(requestId)));
    }
}
