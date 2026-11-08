package com.bbcc.kidly.boundedcontext.wish.app;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.cart.CartOwners;
import com.bbcc.kidly.boundedcontext.cart.app.CartService;
import com.bbcc.kidly.boundedcontext.cart.app.dto.CartView;
import com.bbcc.kidly.boundedcontext.delivery.app.DeliveryService;
import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryRepository;
import com.bbcc.kidly.boundedcontext.delivery.domain.Delivery;
import com.bbcc.kidly.boundedcontext.order.OrderEventDelivery;
import com.bbcc.kidly.boundedcontext.order.PaymentEvents;
import com.bbcc.kidly.boundedcontext.order.app.OrderErrorCode;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderQueryService;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.SellerReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaSeller;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import com.bbcc.kidly.boundedcontext.wish.app.dto.ApproveWishCommand;
import com.bbcc.kidly.boundedcontext.wish.app.dto.WishRequestView;
import com.bbcc.kidly.boundedcontext.wish.app.dto.WishTimelineView;
import com.bbcc.kidly.boundedcontext.wish.app.dto.WishView;
import com.bbcc.kidly.boundedcontext.wish.out.actor.ParentWishActor;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.ErrorCode;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 조르기 흐름(#701): 보내기 · 진행 중 줄 잠금 · 승인(바로 결제 · 보호자 장바구니) · 거절 · 권한, 그리고 결제가 끝난 상품만 공용
 * 장바구니에서 빠지는지. 주문 · 장바구니는 실제 컨텍스트를 같은 앱 안에서 부른다.
 */
@IntegrationTest
class WishRequestIntegrationTest {

    private static final Long CHILD_ID = 1L;
    private static final Long OPTION_A = 1L;
    private static final Long OPTION_B = 2L;
    private static final Long SOLD_OUT_OPTION = 5L;
    private static final long PRICE_A = 25_000L;
    private static final long PRICE_B = 30_000L;
    private static final ParentWishActor PARENT = new ParentWishActor(BUYER_ID);
    private static final ApproveWishCommand.ShippingAddress ADDRESS =
        new ApproveWishCommand.ShippingAddress("보호자", "010-0000-0000", "12345", "서울시");

    @Autowired
    private WishService wishService;

    @Autowired
    private WishRequestService wishRequestService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderQueryService orderQueries;

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderEventDelivery eventDelivery;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private SellerReplicaRepository sellerReplicaRepository;

    @Autowired
    private ChildReplicaRepository childReplicaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private DeliveryRepository deliveryRepository;

    private Long itemA;
    private Long itemB;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(PRICE_A, OCCURRED_AT)));
        productReplicaRepository.save(ReplicaProduct.create(
            ReplicaFixture.product(OPTION_B, PRICE_B, "ON_SALE", false, OCCURRED_AT)));
        sellerReplicaRepository.save(ReplicaSeller.create(
            new ReplicaSeller.Values(SELLER_ID, "키들리 상점", "ACTIVE", LocalDateTime.now())));
        // 자녀 1의 보호자는 BUYER_ID
        childReplicaRepository.save(ReplicaChild.create(ReplicaFixture.child("115.0", false, OCCURRED_AT)));
        TestChildWishActor child = new TestChildWishActor(CHILD_ID);
        itemA = wishService.add(child, CHILD_ID, OPTION_A, 2).line().wishItemId();
        itemB = wishService.add(child, CHILD_ID, OPTION_B, 1).line().wishItemId();
    }

    @Nested
    @DisplayName("보내기 · 진행 중 줄 잠금")
    class Send {

        @Test
        @DisplayName("자녀가 줄 여러 개 + 메시지로 조르면 줄은 공용 장바구니에 남고 그 조르기로 잠긴다")
        void sendsAndKeeps() {
            WishRequestView sent = send(itemA, itemB);

            assertThat(sent.status()).isEqualTo("REQUESTED");
            assertThat(sent.requestedBy()).isEqualTo("CHILD");
            assertThat(sent.message()).isEqualTo("사 주세요");
            assertThat(sent.items()).extracting(WishRequestView.Line::wishItemId,
                    WishRequestView.Line::requestedQuantity, WishRequestView.Line::unitPrice,
                    WishRequestView.Line::saleStatus)
                .containsExactly(tuple(itemA, 2, PRICE_A, "ON_SALE"), tuple(itemB, 1, PRICE_B, "ON_SALE"));
            assertThat(wishService.getWishes(PARENT, CHILD_ID).items())
                .extracting(WishView.Line::wishItemId, WishView.Line::requestId)
                .containsExactlyInAnyOrder(tuple(itemA, sent.requestId()), tuple(itemB, sent.requestId()));
        }

        @Test
        @DisplayName("진행 중인 조르기에 든 줄은 다른 조르기에 넣거나(같은 옵션 다시 담기로) 수량을 바꾸거나 뺄 수 없다(409)")
        void lockedLine() {
            send(itemA);

            assertCode(() -> send(itemB, itemA), WishErrorCode.WISH_ITEM_IN_REQUEST);
            assertCode(() -> wishService.add(PARENT, CHILD_ID, OPTION_A, 1), WishErrorCode.WISH_ITEM_IN_REQUEST);
            assertCode(() -> wishService.remove(PARENT, itemA), WishErrorCode.WISH_ITEM_IN_REQUEST);
            assertThat(lineOf(itemA).quantity()).isEqualTo(2);
            assertThat(lineOf(itemB).requestId()).isNull();
            assertThat(send(itemB).status()).isEqualTo("REQUESTED");
        }

        @Test
        @DisplayName("다른 자녀 줄 · 없는 줄은 404, 줄을 고르지 않거나 겹치면 · 메시지가 100자를 넘으면 400")
        void invalidSend() {
            assertCode(() -> wishRequestService.send(PARENT, CHILD_ID, List.of(itemA, 999L), null),
                WishErrorCode.WISH_ITEM_NOT_FOUND);
            assertCode(() -> wishRequestService.send(PARENT, CHILD_ID, List.of(), null),
                WishErrorCode.INVALID_WISH_REQUEST);
            assertCode(() -> wishRequestService.send(PARENT, CHILD_ID, List.of(itemA, itemA), null),
                WishErrorCode.INVALID_WISH_REQUEST);
            assertCode(() -> wishRequestService.send(PARENT, CHILD_ID, List.of(itemA), "가".repeat(101)),
                WishErrorCode.INVALID_WISH_REQUEST);
            assertThat(lineOf(itemA).requestId()).isNull();
        }
    }

    @Nested
    @DisplayName("바로 결제")
    class Order {

        @Test
        @DisplayName("일부만 · 수량을 바꿔 승인하면 기존 주문하기로 보호자 주문을 만들고(주문상품 자녀 = 조른 자녀) 주문 ID를 기록한다")
        void placesOrder() {
            Long requestId = send(itemA, itemB).requestId();

            WishRequestView approved = approveOrder(requestId, 3);

            assertThat(approved.status()).isEqualTo("APPROVED");
            assertThat(approved.checkout()).isEqualTo("ORDER");
            assertThat(approved.orderId()).isNotNull();
            assertThat(approved.items()).extracting(WishRequestView.Line::wishItemId,
                    WishRequestView.Line::approvedQuantity)
                .containsExactly(tuple(itemA, 3), tuple(itemB, null));
            OrderDetail order = orderQueries.getOrder(BUYER_ID, approved.orderId());
            assertThat(order.status()).isEqualTo("PENDING_PAYMENT");
            assertThat(order.items()).extracting(OrderDetail.Item::optionId, OrderDetail.Item::quantity,
                    item -> item.child().childId())
                .containsExactly(tuple(OPTION_A, 3, CHILD_ID));
            // 결제 전: 승인한 줄은 잠긴 채 남고, 승인하지 않은 줄은 풀린다
            assertThat(lineOf(itemA).requestId()).isEqualTo(requestId);
            assertThat(lineOf(itemB).requestId()).isNull();
        }

        @Test
        @DisplayName("결제가 끝나면 승인한 줄만 공용 장바구니에서 빠진다(보기는 바로 반영, 저장은 다음 쓰기 때)")
        void paidLeaves() {
            Long requestId = send(itemA, itemB).requestId();
            Long orderId = approveOrder(requestId, 2).orderId();

            pay(orderId);

            assertThat(wishService.getWishes(PARENT, CHILD_ID).items()).extracting(WishView.Line::wishItemId)
                .containsExactly(itemB);
            assertThat(wishRequestService.getRequest(PARENT, requestId).orderResult()).isEqualTo("PAID");
            assertThat(statusInDb(itemA)).isEqualTo("ACTIVE");

            wishService.add(PARENT, CHILD_ID, OPTION_B, 1);

            assertThat(statusInDb(itemA)).isEqualTo("PURCHASED");
            assertThat(jdbcTemplate.queryForObject("select order_result from orders.wish_request where id = ?",
                String.class, requestId)).isEqualTo("PAID");
            // 같은 옵션을 다시 담으면 산 줄과 합치지 않고 새 줄이다
            assertThat(wishService.add(PARENT, CHILD_ID, OPTION_A, 1).created()).isTrue();
        }

        @Test
        @DisplayName("결제 실패로 주문이 취소되면 승인한 줄은 공용 장바구니에 남고 잠금이 풀린다")
        void paymentFailedStays() {
            Long requestId = send(itemA).requestId();
            Long orderId = approveOrder(requestId, 2).orderId();
            OrderDetail order = orderQueries.getOrder(BUYER_ID, orderId);

            eventDelivery.deliver(stockDeducted(orderId), PaymentEvents.insufficientBalance(order, BUYER_ID));

            assertThat(lineOf(itemA).requestId()).isNull();
            assertThat(wishRequestService.getRequest(PARENT, requestId).orderResult()).isEqualTo("CANCELLED");
            assertThat(send(itemA).status()).isEqualTo("REQUESTED");
        }

        @Test
        @DisplayName("결제 전에는 남고 잠긴 채다")
        void pendingStaysLocked() {
            Long requestId = send(itemA).requestId();
            approveOrder(requestId, 2);

            assertThat(lineOf(itemA).requestId()).isEqualTo(requestId);
            assertThat(wishRequestService.getRequest(PARENT, requestId).orderResult()).isNull();
        }

        @Test
        @DisplayName("주문하기가 거절하면(화면 가격이 지금 가격과 다름) 그 오류가 나가고 승인도 롤백된다")
        void orderRejectedRollsBack() {
            Long requestId = send(itemA).requestId();

            assertCode(() -> wishRequestService.approve(PARENT, requestId, new ApproveWishCommand("ORDER",
                List.of(new ApproveWishCommand.Item(itemA, 1, PRICE_A - 1)), ADDRESS)), OrderErrorCode.PRICE_CHANGED);

            WishRequestView after = wishRequestService.getRequest(PARENT, requestId);
            assertThat(after.status()).isEqualTo("REQUESTED");
            assertThat(after.orderId()).isNull();
            assertThat(lineOf(itemA).requestId()).isEqualTo(requestId);
        }

        @Test
        @DisplayName("바로 결제에 가격 · 배송지가 없거나 구매 방법을 모르면 400")
        void invalidOrderApproval() {
            Long requestId = send(itemA).requestId();

            assertCode(() -> wishRequestService.approve(PARENT, requestId, new ApproveWishCommand("ORDER",
                List.of(new ApproveWishCommand.Item(itemA, 1, null)), ADDRESS)), WishErrorCode.INVALID_APPROVAL);
            assertCode(() -> wishRequestService.approve(PARENT, requestId, new ApproveWishCommand("ORDER",
                List.of(new ApproveWishCommand.Item(itemA, 1, PRICE_A)), null)), WishErrorCode.INVALID_APPROVAL);
            assertCode(() -> wishRequestService.approve(PARENT, requestId, new ApproveWishCommand("GIFT",
                List.of(new ApproveWishCommand.Item(itemA, 1, PRICE_A)), ADDRESS)), WishErrorCode.INVALID_APPROVAL);
            assertCode(() -> wishRequestService.approve(PARENT, requestId, new ApproveWishCommand("ORDER",
                List.of(new ApproveWishCommand.Item(itemB, 1, PRICE_B)), ADDRESS)), WishErrorCode.INVALID_APPROVAL);
        }
    }

    @Nested
    @DisplayName("타임라인(#702)")
    class Timeline {

        @Test
        @DisplayName("처리 전: 보냄 DONE → 승인 WAITING. 거절: 보냄 → 거절(사유), 주문 단계 없음")
        void requestedAndRejected() {
            Long requestId = send(itemA).requestId();

            assertThat(steps(wishRequestService.getTimeline(PARENT, requestId)))
                .containsExactly(tuple("SENT", "DONE", true), tuple("APPROVED", "WAITING", false));

            wishRequestService.reject(PARENT, requestId, "TOO_EXPENSIVE");

            WishTimelineView rejected = wishRequestService.getTimeline(PARENT, requestId);
            assertThat(rejected.status()).isEqualTo("REJECTED");
            assertThat(rejected.rejectReason()).isEqualTo("TOO_EXPENSIVE");
            assertThat(rejected.orderId()).isNull();
            assertThat(steps(rejected)).containsExactly(tuple("SENT", "DONE", true), tuple("REJECTED", "DONE", true));
        }

        @Test
        @DisplayName("장바구니로 옮기기로 승인: 주문이 없어 조르기 단계로 끝난다")
        void approvedWithoutOrder() {
            Long requestId = send(itemA).requestId();
            wishRequestService.approve(PARENT, requestId, new ApproveWishCommand("CART",
                List.of(new ApproveWishCommand.Item(itemA, 1, null)), null));

            WishTimelineView timeline = wishRequestService.getTimeline(PARENT, requestId);

            assertThat(timeline.checkout()).isEqualTo("CART");
            assertThat(steps(timeline)).containsExactly(tuple("SENT", "DONE", true), tuple("APPROVED", "DONE", true));
        }

        @Test
        @DisplayName("바로 결제: 조르기 단계 뒤에 주문 타임라인을 이어 붙인다(결제대기 → 결제 → 발송 → 배송 완료 → 구매확정)")
        void approvedWithOrder() {
            Long requestId = send(itemA).requestId();
            Long orderId = approveOrder(requestId, 2).orderId();

            WishTimelineView pending = wishRequestService.getTimeline(PARENT, requestId);
            assertThat(pending.orderId()).isEqualTo(orderId);
            assertThat(steps(pending)).containsExactly(tuple("SENT", "DONE", true), tuple("APPROVED", "DONE", true),
                tuple("CREATED", "DONE", true), tuple("PAYMENT_REQUESTED", "WAITING", false),
                tuple("PAID", "WAITING", false), tuple("SHIPPED", "WAITING", false),
                tuple("DELIVERED", "WAITING", false), tuple("CONFIRMED", "WAITING", false));

            pay(orderId);
            Delivery delivery = deliveryRepository.findAllByOrderIdOrderByIdAsc(orderId).getFirst();
            deliveryService.ship(SELLER_ID, delivery.getId(), "CJ", "1234567890");
            deliveryService.complete(SELLER_ID, delivery.getId());
            Long orderItemId = orderQueries.getOrder(BUYER_ID, orderId).items().getFirst().orderItemId();
            orderService.confirmByBuyer(BUYER_ID, orderId, orderItemId);

            assertThat(steps(wishRequestService.getTimeline(new TestChildWishActor(CHILD_ID), requestId)))
                .as("자녀가 봐도 보호자의 주문 타임라인이 이어진다")
                .containsExactly(tuple("SENT", "DONE", true), tuple("APPROVED", "DONE", true),
                    tuple("CREATED", "DONE", true), tuple("PAYMENT_REQUESTED", "DONE", true),
                    tuple("PAID", "DONE", true), tuple("SHIPPED", "DONE", true), tuple("DELIVERED", "DONE", true),
                    tuple("CONFIRMED", "DONE", true));
        }

        @Test
        @DisplayName("결제 실패로 주문이 취소되면 주문 타임라인 그대로 CANCELLED가 이어진다")
        void orderCancelled() {
            Long requestId = send(itemA).requestId();
            Long orderId = approveOrder(requestId, 2).orderId();
            eventDelivery.deliver(stockDeducted(orderId),
                PaymentEvents.insufficientBalance(orderQueries.getOrder(BUYER_ID, orderId), BUYER_ID));

            assertThat(wishRequestService.getTimeline(PARENT, requestId).steps())
                .extracting(WishTimelineView.Step::step, WishTimelineView.Step::status)
                .contains(tuple("CANCELLED", "DONE"), tuple("SHIPPED", "NOT_APPLICABLE"));
        }

        @Test
        @DisplayName("남의 자녀 조르기의 타임라인은 404")
        void otherParent() {
            Long requestId = send(itemA).requestId();

            assertCode(() -> wishRequestService.getTimeline(new ParentWishActor(3L), requestId),
                WishErrorCode.WISH_REQUEST_NOT_FOUND);
        }

        private List<org.assertj.core.groups.Tuple> steps(WishTimelineView timeline) {
            return timeline.steps().stream()
                .map(step -> tuple(step.step(), step.status(), step.at() != null))
                .toList();
        }
    }

    @Test
    @DisplayName("보호자 장바구니로 옮기기: 승인한 줄을 보호자 장바구니에 조른 자녀로 담고, 공용 장바구니 줄은 남고 풀린다")
    void approveToCart() {
        Long requestId = send(itemA, itemB).requestId();

        WishRequestView approved = wishRequestService.approve(PARENT, requestId, new ApproveWishCommand("CART",
            List.of(new ApproveWishCommand.Item(itemB, 4, null)), null));

        assertThat(approved.checkout()).isEqualTo("CART");
        assertThat(approved.orderId()).isNull();
        assertThat(cartService.getCart(CartOwners.member(BUYER_ID)).items())
            .extracting(CartView.Line::optionId, CartView.Line::childId, CartView.Line::quantity)
            .containsExactly(tuple(OPTION_B, CHILD_ID, 4));
        assertThat(wishService.getWishes(PARENT, CHILD_ID).items())
            .extracting(WishView.Line::wishItemId, WishView.Line::requestId)
            .containsExactlyInAnyOrder(tuple(itemA, null), tuple(itemB, null));
    }

    @Test
    @DisplayName("거절: 고정 목록의 사유를 남기고 잠금을 푼다. 목록에 없는 사유는 400, 이미 처리했으면 409")
    void reject() {
        Long requestId = send(itemA).requestId();

        assertCode(() -> wishRequestService.reject(PARENT, requestId, "BORING"), WishErrorCode.INVALID_REJECT_REASON);
        WishRequestView rejected = wishRequestService.reject(PARENT, requestId, "ALREADY_HAVE");

        assertThat(rejected.status()).isEqualTo("REJECTED");
        assertThat(rejected.rejectReason()).isEqualTo("ALREADY_HAVE");
        assertThat(rejected.decidedAt()).isNotNull();
        assertThat(lineOf(itemA).requestId()).isNull();
        assertCode(() -> wishRequestService.reject(PARENT, requestId, "OTHER"),
            WishErrorCode.WISH_REQUEST_ALREADY_DECIDED);
        assertCode(() -> approveOrder(requestId, 1), WishErrorCode.WISH_REQUEST_ALREADY_DECIDED);
    }

    @Test
    @DisplayName("권한: 승인 · 거절은 보호자만(자녀 403), 남의 자녀 조르기는 보기 · 승인 · 거절 모두 404")
    void permissions() {
        Long requestId = send(itemA).requestId();
        TestChildWishActor child = new TestChildWishActor(CHILD_ID);
        ParentWishActor stranger = new ParentWishActor(3L);

        assertCode(() -> wishRequestService.reject(child, requestId, "OTHER"),
            WishErrorCode.WISH_REQUEST_DECIDE_FORBIDDEN);
        assertCode(() -> wishRequestService.approve(child, requestId, new ApproveWishCommand("CART",
            List.of(new ApproveWishCommand.Item(itemA, 1, null)), null)), WishErrorCode.WISH_REQUEST_DECIDE_FORBIDDEN);
        assertCode(() -> wishRequestService.getRequest(stranger, requestId), WishErrorCode.WISH_REQUEST_NOT_FOUND);
        assertCode(() -> wishRequestService.reject(stranger, requestId, "OTHER"),
            WishErrorCode.WISH_REQUEST_NOT_FOUND);
        assertCode(() -> wishRequestService.getRequests(stranger, CHILD_ID), WishErrorCode.CHILD_NOT_FOUND);
        assertCode(() -> wishRequestService.send(stranger, CHILD_ID, List.of(itemB), null),
            WishErrorCode.CHILD_NOT_FOUND);
        assertThat(wishRequestService.getRequest(child, requestId).status()).isEqualTo("REQUESTED");
    }

    @Test
    @DisplayName("판매 중이 아닌 상품도 빼지 않고 지금 가격 · 판매 상태 값으로 보인다")
    void showsSaleStatus() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.option(
            SOLD_OUT_OPTION, "ON_SALE", "ON_SALE", false, OCCURRED_AT)));
        Long soldOut = wishService.add(PARENT, CHILD_ID, SOLD_OUT_OPTION, 1).line().wishItemId();
        Long requestId = send(soldOut).requestId();
        jdbcTemplate.update("update orders.replica_product set is_sold_out = true where option_id = ?",
            SOLD_OUT_OPTION);

        assertThat(lineOf(soldOut).saleStatus()).isEqualTo("SOLD_OUT");
        assertThat(lineOf(soldOut).purchasable()).isFalse();
        assertThat(lineOf(itemA).saleStatus()).isEqualTo("ON_SALE");
        assertThat(wishRequestService.getRequests(PARENT, CHILD_ID)).singleElement()
            .satisfies(request -> assertThat(request.requestId()).isEqualTo(requestId))
            .extracting(WishRequestView::items).asInstanceOf(InstanceOfAssertFactories.LIST)
            .singleElement()
            .isEqualTo(new WishRequestView.Line(soldOut, SOLD_OUT_OPTION, 1L, SELLER_ID, "상품 A", "네이비 / 110",
                "110", "키들리 상점", 1, null, 25_000L, false, "SOLD_OUT"));
    }

    private WishRequestView send(Long... wishItemIds) {
        return wishRequestService.send(new TestChildWishActor(CHILD_ID), CHILD_ID, List.of(wishItemIds), "사 주세요");
    }

    private WishRequestView approveOrder(Long requestId, int quantityA) {
        return wishRequestService.approve(PARENT, requestId, new ApproveWishCommand("ORDER",
            List.of(new ApproveWishCommand.Item(itemA, quantityA, PRICE_A)), ADDRESS));
    }

    private void pay(Long orderId) {
        eventDelivery.deliver(stockDeducted(orderId));
        eventDelivery.deliver(PaymentEvents.completed(orderQueries.getOrder(BUYER_ID, orderId), BUYER_ID));
    }

    private static StockDeductedEvent stockDeducted(Long orderId) {
        return new StockDeductedEvent(UUID.randomUUID(), LocalDateTime.now(), new StockDeductedEvent.Data(orderId));
    }

    private WishView.Line lineOf(Long wishItemId) {
        return wishService.getWishes(PARENT, CHILD_ID).items().stream()
            .filter(line -> line.wishItemId().equals(wishItemId))
            .findFirst()
            .orElseThrow();
    }

    private String statusInDb(Long wishItemId) {
        return jdbcTemplate.queryForObject("select status from orders.wish_item where id = ?", String.class,
            wishItemId);
    }

    private static void assertCode(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}
