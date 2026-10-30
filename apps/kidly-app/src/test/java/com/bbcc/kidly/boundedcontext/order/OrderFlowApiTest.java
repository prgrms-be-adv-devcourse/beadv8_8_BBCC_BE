package com.bbcc.kidly.boundedcontext.order;

import static com.bbcc.kidly.boundedcontext.order.in.web.CartController.CART_ITEMS_PATH;
import static com.bbcc.kidly.boundedcontext.order.in.web.CartController.CART_PATH;
import static com.bbcc.kidly.boundedcontext.order.in.web.OrderController.ORDERS_PATH;
import static com.bbcc.kidly.boundedcontext.order.in.web.SellerOrderController.SELLER_ORDERS_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.order.app.order.OrderScheduleIntervals;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderSchedulerProperties;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.PaymentExpiryGate;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.in.scheduler.OrderScheduler;
import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import com.bbcc.kidly.shared.order.event.OrderCreatedEvent;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import com.bbcc.kidly.shared.order.event.OrderPaymentRequestedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentFailedEvent;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.domain.StockDeductionFailureCode;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductionFailedEvent;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 주문 전 구간 흐름: ④ 주문 · 결제 → ⑤ 배송 → ⑥ 구매확정.
 * 사용자 행동(담기 · 주문하기 · 발송 · 배송 완료 · 구매확정 · 조회)은 API로, 상품 · 자녀 도메인 사건은 공유 계약 이벤트를
 * 직접 발행해 흉내 낸다. 결제는 결제 도메인 리스너가 실제로 돈다(지갑이 있으면 결제 완료됨, 없으면 결제 실패됨).
 * 시간은 실행 시각 기준 상대값, 과거로 옮겨야 하는 마감 · 배송 완료 시각은 DB 값을 직접 바꾼다.
 * 스케줄러는 테스트 프로필에서 저절로 돌지 않으므로 메서드를 직접 부른다.
 */
@FunctionalTest
class OrderFlowApiTest {

    private static final OrderScheduleIntervals INTERVALS = new OrderScheduleIntervals(
        java.time.Duration.ofMinutes(1), new OrderScheduleIntervals.AutoConfirm(java.time.Duration.ofMinutes(10)));

    private static final Long BUYER_ID = 1L;
    private static final Long SELLER_A_ID = 2L;
    private static final Long SELLER_B_ID = 3L;
    private static final Long CHILD_ID = 1L;
    private static final long ORDER_AMOUNT = 25_000L * 2 + 30_000L;
    private static final Map<String, String> ADDRESS = Map.of(
        "recipientName", "홍길동", "phone", "010-0000-0000", "zipCode", "12345", "address", "서울시 테스트로 1");

    @Autowired
    private OrderEventDelivery eventDelivery;

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrderScheduler orderScheduler;

    @Autowired
    private OrderService orderService;

    // 결제 이벤트 Kafka 리스너가 없어 만료를 늘 허락한다(#561)
    @Autowired
    private PaymentExpiryGate paymentExpiryGate;

    @Autowired
    private Order.Policy orderPolicy;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private OrderFlowEventRecorder recorder;

    @BeforeEach
    void setUp() {
        publishReplicaEvents();
        recorder.clear();
    }

    @Test
    @DisplayName("정상 흐름: 담기 → 주문 → 재고 차감 → 결제 완료 → 판매자별 발송 · 배송 완료 → 직접 구매확정")
    void happyPath() {
        addToCart("1", "1", 2);
        addToCart("2", null, 1);
        addToCart("1", null, 1);
        saveBuyerWallet(ORDER_AMOUNT);

        Long orderId = placeOrder();

        OrderCreatedEvent created = single(OrderCreatedEvent.class, orderId);
        assertThat(fieldNames(OrderCreatedEvent.Data.class)).containsExactly("orderId", "memberId", "items");
        assertThat(fieldNames(OrderCreatedEvent.Item.class)).containsExactly("orderItemId", "optionId", "quantity");
        assertThat(created.data().memberId()).isEqualTo(BUYER_ID);
        assertThat(created.data().items()).extracting(OrderCreatedEvent.Item::optionId).containsExactly(1L, 2L);
        assertThat(created.data().items()).extracting(OrderCreatedEvent.Item::quantity).containsExactly(2, 1);

        eventDelivery.deliver(stockDeducted(orderId));

        OrderPaymentRequestedEvent requested = single(OrderPaymentRequestedEvent.class, orderId);
        assertThat(fieldNames(OrderPaymentRequestedEvent.Data.class))
            .containsExactly("orderId", "memberId", "amount", "items");
        assertThat(fieldNames(OrderPaymentRequestedEvent.Item.class)).containsExactly("orderItemId", "amount");
        assertThat(requested.data().memberId()).isEqualTo(BUYER_ID);
        assertThat(requested.data().amount()).isEqualTo(ORDER_AMOUNT);
        assertThat(requested.data().items()).extracting(OrderPaymentRequestedEvent.Item::orderItemId)
            .containsExactlyElementsOf(created.data().items().stream().map(OrderCreatedEvent.Item::orderItemId)
                .toList());
        assertThat(requested.data().items().stream().mapToLong(OrderPaymentRequestedEvent.Item::amount).sum())
            .isEqualTo(requested.data().amount());
        List<PaymentCompletedEvent> completed = paymentCompleted(orderId);
        assertThat(completed).hasSize(1);
        // 실제 결제 모듈이 보낸 회원 · 주문상품별 금액이 주문과 정확히 같아야 결제완료가 된다(#215, 할인 · 반올림 차이 없음)
        assertThat(completed.get(0).data().memberId()).isEqualTo(BUYER_ID);
        assertThat(completed.get(0).data().items())
            .extracting(PaymentCompletedEvent.Item::orderItemId, PaymentCompletedEvent.Item::amount)
            .containsExactlyElementsOf(requested.data().items().stream()
                .map(item -> tuple(item.orderItemId(), item.amount()))
                .toList());
        assertThat(detail(orderId).items()).hasSize(2);

        OrderBody paid = detail(orderId);
        assertThat(paid.status()).isEqualTo("PAID");
        assertThat(paid.deliveries()).extracting(DeliveryRow::sellerId)
            .containsExactlyInAnyOrder(String.valueOf(SELLER_A_ID), String.valueOf(SELLER_B_ID));
        assertThat(cart()).extracting(CartLine::optionId, CartLine::childId)
            .containsExactly(tuple("1", null));

        shipAndDeliver(SELLER_A_ID, orderId);
        shipAndDeliver(SELLER_B_ID, orderId);

        OrderBody delivered = detail(orderId);
        assertThat(delivered.deliveries()).allSatisfy(delivery -> {
            assertThat(delivery.status()).isEqualTo("DELIVERED");
            assertThat(delivery.deliveredAt()).isNotNull();
        });
        assertThat(delivered.items()).allSatisfy(item -> assertThat(item.deliveredAt()).isNotNull());

        ItemRow itemOfA = itemOfSeller(delivered, SELLER_A_ID);
        client.post().uri(ORDERS_PATH + "/" + orderId + "/items/" + itemOfA.orderItemId() + "/confirm")
            .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
            .exchange()
            .expectStatus().isOk();

        OrderItemPurchaseConfirmedEvent confirmed = single(OrderItemPurchaseConfirmedEvent.class, orderId);
        assertThat(fieldNames(OrderItemPurchaseConfirmedEvent.Data.class)).containsExactly(
            "orderItemId", "orderId", "memberId", "sellerId", "childId", "productId", "optionId", "size",
            "quantity", "amount", "confirmedAt");
        OrderItemPurchaseConfirmedEvent.Data data = confirmed.data();
        assertThat(data.orderItemId()).isEqualTo(Long.valueOf(itemOfA.orderItemId()));
        assertThat(data.orderId()).isEqualTo(orderId);
        assertThat(data.memberId()).isEqualTo(BUYER_ID);
        assertThat(data.sellerId()).isEqualTo(SELLER_A_ID);
        assertThat(data.childId()).isEqualTo(CHILD_ID);
        assertThat(data.productId()).isEqualTo(1L);
        assertThat(data.optionId()).isEqualTo(1L);
        assertThat(data.size()).isEqualTo("110");
        assertThat(data.quantity()).isEqualTo(2);
        assertThat(data.amount()).isEqualTo(50_000L);
        assertThat(data.confirmedAt()).isNotNull();
        ItemRow confirmedItem = itemOfSeller(detail(orderId), SELLER_A_ID);
        assertThat(confirmedItem.status()).isEqualTo("CONFIRMED");
        assertThat(confirmedItem.confirmType()).isEqualTo("DIRECT");
    }

    @Test
    @DisplayName("자동 구매확정: 배송 완료 후 기간이 지난 주문상품은 스케줄러가 AUTO로 확정하고 이벤트를 발행한다")
    void autoConfirm() {
        saveBuyerWallet(ORDER_AMOUNT);
        Long orderId = placeOrder();
        eventDelivery.deliver(stockDeducted(orderId));
        shipAndDeliver(SELLER_A_ID, orderId);
        ItemRow itemOfA = itemOfSeller(detail(orderId), SELLER_A_ID);
        jdbcTemplate.update("update orders.order_item set delivered_at = ? where id = ?",
            LocalDateTime.now().minus(orderPolicy.autoConfirmAfter()).minusMinutes(1),
            Long.valueOf(itemOfA.orderItemId()));

        orderScheduler.autoConfirm();

        ItemRow item = itemOfSeller(detail(orderId), SELLER_A_ID);
        assertThat(item.status()).isEqualTo("CONFIRMED");
        assertThat(item.confirmType()).isEqualTo("AUTO");
        assertThat(single(OrderItemPurchaseConfirmedEvent.class, orderId).data().orderItemId())
            .isEqualTo(Long.valueOf(itemOfA.orderItemId()));
        assertThat(itemOfSeller(detail(orderId), SELLER_B_ID).status()).isEqualTo("ORDERED");
    }

    @Test
    @DisplayName("결제 실패: 지갑이 없으면 결제 실패됨 → PAYMENT_FAILED로 취소 · order.cancelled, 장바구니는 그대로")
    void paymentFailed() {
        addToCart("1", "1", 2);
        addToCart("2", null, 1);
        Long orderId = placeOrder();
        List<Long> orderItemIds = single(OrderCreatedEvent.class, orderId).data().items().stream()
            .map(OrderCreatedEvent.Item::orderItemId).toList();

        eventDelivery.deliver(stockDeducted(orderId));

        assertThat(recorder.events(PaymentFailedEvent.class))
            .filteredOn(event -> event.data().orderId().equals(orderId)).hasSize(1);
        OrderBody order = detail(orderId);
        assertThat(order.status()).isEqualTo("CANCELLED");
        assertThat(order.cancelReason()).isEqualTo("PAYMENT_FAILED");
        OrderCancelledEvent cancelled = single(OrderCancelledEvent.class, orderId);
        assertThat(fieldNames(OrderCancelledEvent.Data.class))
            .containsExactly("orderId", "memberId", "reason", "items");
        assertThat(fieldNames(OrderCancelledEvent.Item.class)).containsExactly("orderItemId", "optionId", "quantity");
        assertThat(cancelled.data().memberId()).isEqualTo(BUYER_ID);
        assertThat(cancelled.data().reason()).isEqualTo("PAYMENT_FAILED");
        assertThat(cancelled.data().items()).extracting(OrderCancelledEvent.Item::orderItemId)
            .containsExactlyElementsOf(orderItemIds);
        assertThat(cancelled.data().items()).extracting(OrderCancelledEvent.Item::quantity).containsExactly(2, 1);
        assertThat(cart()).hasSize(2);
    }

    @Test
    @DisplayName("재고 차감 실패: 재고 부족함 → OUT_OF_STOCK으로 취소 · order.cancelled, 상세에 실패한 주문상품만 사유, "
        + "늦게 온 재고 차감됨은 결제를 요청하지 않고 판매 주문 목록에도 나오지 않는다")
    void stockDeductionFailed() {
        addToCart("1", "1", 2);
        addToCart("2", null, 1);
        Long orderId = placeOrder();
        List<Long> orderItemIds = single(OrderCreatedEvent.class, orderId).data().items().stream()
            .map(OrderCreatedEvent.Item::orderItemId).toList();

        eventDelivery.deliver(new StockDeductionFailedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new StockDeductionFailedEvent.Data(orderId, StockDeductionFailureCode.OUT_OF_STOCK,
                List.of(new StockDeductionFailedEvent.Failure(
                    orderItemIds.get(1), StockDeductionFailureCode.OUT_OF_STOCK)))));

        client.get().uri(ORDERS_PATH + "/" + orderId)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("CANCELLED")
            .jsonPath("$.cancelReason").isEqualTo("OUT_OF_STOCK")
            .jsonPath("$.items[0].orderItemId").isEqualTo(String.valueOf(orderItemIds.get(0)))
            .jsonPath("$.items[0].unavailableReason").value(reason -> assertThat(reason).isNull())
            .jsonPath("$.items[1].orderItemId").isEqualTo(String.valueOf(orderItemIds.get(1)))
            .jsonPath("$.items[1].unavailableReason").isEqualTo("OUT_OF_STOCK");
        OrderCancelledEvent cancelled = single(OrderCancelledEvent.class, orderId);
        assertThat(cancelled.data().reason()).isEqualTo("OUT_OF_STOCK");
        assertThat(cancelled.data().items()).extracting(OrderCancelledEvent.Item::orderItemId)
            .containsExactlyElementsOf(orderItemIds);
        assertThat(cart()).hasSize(2);

        eventDelivery.deliver(stockDeducted(orderId));

        assertThat(recorder.events(OrderPaymentRequestedEvent.class))
            .filteredOn(event -> event.data().orderId().equals(orderId)).isEmpty();
        assertThat(detail(orderId).status()).isEqualTo("CANCELLED");
        for (Long sellerId : List.of(SELLER_A_ID, SELLER_B_ID)) {
            client.get().uri(SELLER_ORDERS_PATH)
                .headers(headers -> headers.setBearerAuth(tokens.seller(sellerId)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content[?(@.orderId == '" + orderId + "')]").isEmpty();
        }
    }

    @Test
    @DisplayName("결제 마감 만료: 스케줄러가 PAYMENT_EXPIRED로 취소, 그 뒤 온 결제 완료됨은 무시(상태 그대로 · 배송 없음)")
    void paymentExpired() {
        Long orderId = placeOrder();
        expire(orderId);

        orderScheduler.expireOverduePayments();

        OrderBody expired = detail(orderId);
        assertThat(expired.status()).isEqualTo("CANCELLED");
        assertThat(expired.cancelReason()).isEqualTo("PAYMENT_EXPIRED");
        assertThat(single(OrderCancelledEvent.class, orderId).data().reason()).isEqualTo("PAYMENT_EXPIRED");

        eventDelivery.deliver(new PaymentCompletedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new PaymentCompletedEvent.Data(
                999L, orderId, BUYER_ID, ORDER_AMOUNT, List.of(), LocalDateTime.now())));

        OrderBody after = detail(orderId);
        assertThat(after.status()).isEqualTo("CANCELLED");
        assertThat(after.cancelReason()).isEqualTo("PAYMENT_EXPIRED");
        assertThat(after.deliveries()).isEmpty();
    }

    @Test
    @DisplayName("취소 후 늦게 온 재고 차감됨: 결제를 요청하지 않고 상태 그대로")
    void lateStockDeductedAfterCancel() {
        Long orderId = placeOrder();
        expire(orderId);
        orderScheduler.expireOverduePayments();

        eventDelivery.deliver(stockDeducted(orderId));

        assertThat(recorder.events(OrderPaymentRequestedEvent.class))
            .filteredOn(event -> event.data().orderId().equals(orderId)).isEmpty();
        OrderBody order = detail(orderId);
        assertThat(order.status()).isEqualTo("CANCELLED");
        assertThat(order.cancelReason()).isEqualTo("PAYMENT_EXPIRED");
    }

    @Test
    @DisplayName("키 · 몸무게 없는 자녀 삭제 이벤트도 반영돼, 그 자녀로 주문하면 기존처럼 409 CHILD_NOT_ORDERABLE")
    void deletedChildWithoutBodyValuesCannotBeOrdered() {
        eventDelivery.deliver(new ChildChangedEvent(UUID.randomUUID(), LocalDateTime.now().plusMinutes(1),
            new ChildChangedEvent.Data(
                CHILD_ID, BUYER_ID, null, null, Gender.MALE, LocalDate.now().minusYears(7), BodyType.NORMAL,
                PreferredFit.LOOSE, true)));

        client.post().uri(ORDERS_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
            .body(Map.of(
                "items", List.of(Map.of("optionId", "1", "childId", String.valueOf(CHILD_ID), "quantity", 1,
                    "unitPrice", 25_000)),
                "shippingAddress", ADDRESS))
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.title").isEqualTo("선택할 수 없는 자녀입니다")
            .jsonPath("$.detail").isEqualTo("삭제됐거나 내 자녀가 아닌 자녀입니다. 자녀를 다시 선택해 주세요.");
    }

    @Test
    @DisplayName("결제 마감 만료(여러 배치): 마감이 지난 주문 3건을 batch 2로 한 실행에 끝까지 PAYMENT_EXPIRED로 취소한다")
    void paymentExpiryAcrossBatches() {
        List<Long> orderIds = List.of(placeOrder(), placeOrder(), placeOrder());
        orderIds.forEach(this::expire);

        smallBatchScheduler().expireOverduePayments();

        for (Long orderId : orderIds) {
            OrderBody order = detail(orderId);
            assertThat(order.status()).isEqualTo("CANCELLED");
            assertThat(order.cancelReason()).isEqualTo("PAYMENT_EXPIRED");
        }
        assertThat(recorder.events(OrderCancelledEvent.class)).extracting(event -> event.data().orderId())
            .containsExactlyInAnyOrderElementsOf(orderIds);
    }

    @Test
    @DisplayName("자동 구매확정(여러 배치): 기간이 지난 주문상품 4건(주문 2건 × 판매자 2명)을 batch 2로 한 실행에 끝까지 AUTO 확정한다")
    void autoConfirmAcrossBatches() {
        saveBuyerWallet(ORDER_AMOUNT * 2);
        List<Long> orderIds = List.of(placeOrder(), placeOrder());
        for (Long orderId : orderIds) {
            eventDelivery.deliver(stockDeducted(orderId));
            shipAndDeliver(SELLER_A_ID, orderId);
            shipAndDeliver(SELLER_B_ID, orderId);
            jdbcTemplate.update("update orders.order_item set delivered_at = ? where order_id = ?",
                LocalDateTime.now().minus(orderPolicy.autoConfirmAfter()).minusMinutes(1), orderId);
        }

        smallBatchScheduler().autoConfirm();

        for (Long orderId : orderIds) {
            assertThat(detail(orderId).items()).hasSize(2).allSatisfy(item -> {
                assertThat(item.status()).isEqualTo("CONFIRMED");
                assertThat(item.confirmType()).isEqualTo("AUTO");
            });
        }
        assertThat(recorder.events(OrderItemPurchaseConfirmedEvent.class)).hasSize(4);
    }

    /**
     * 시연처럼 짧게: 한 번 조회 2건 · 한 실행 상한 10건. 같은 컨텍스트의 실제 서비스로 돌린다.
     */
    private OrderScheduler smallBatchScheduler() {
        return new OrderScheduler(orderService, new OrderSchedulerProperties(2, 10), INTERVALS, paymentExpiryGate);
    }

    @Test
    @DisplayName("중복 이벤트: 같은 재고 차감됨 두 번 → 결제 요청 1번, 같은 결제 완료됨 두 번 → 판매자당 배송 1건")
    void duplicateEvents() {
        saveBuyerWallet(ORDER_AMOUNT);
        Long orderId = placeOrder();
        StockDeductedEvent stockDeducted = stockDeducted(orderId);

        eventDelivery.deliver(stockDeducted);
        eventDelivery.deliver(stockDeducted);

        assertThat(recorder.events(OrderPaymentRequestedEvent.class))
            .filteredOn(event -> event.data().orderId().equals(orderId)).hasSize(1);
        List<PaymentCompletedEvent> completed = paymentCompleted(orderId);
        assertThat(completed).hasSize(1);

        eventDelivery.deliver(completed.get(0));

        OrderBody order = detail(orderId);
        assertThat(order.status()).isEqualTo("PAID");
        assertThat(order.deliveries()).extracting(DeliveryRow::sellerId)
            .containsExactlyInAnyOrder(String.valueOf(SELLER_A_ID), String.valueOf(SELLER_B_ID));
    }

    @Test
    @DisplayName("사이즈 없는 상품(size null): 담기 → 주문 → 결제 완료 → 배송 완료 → 구매확정까지 500 없이 되고, 응답 · 이벤트의 size는 null")
    void sizeNullProductFlow() {
        LocalDateTime now = LocalDateTime.now();
        eventDelivery.deliver(new ProductChangedEvent(UUID.randomUUID(), now, new ProductChangedEvent.Data(
            3L, SELLER_A_ID, "양말", 1L, ProductStatus.ON_SALE, null, now.minusDays(1), true,
            List.of(new ProductChangedEvent.Option(30L, "색상: 흰색", null, 5_000L,
                null, null, false, OptionSaleStatus.ON_SALE)))));
        addToCart("30", null, 1);
        client.get().uri(CART_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.items[0].optionId").isEqualTo("30")
            .jsonPath("$.items[0].size").value(size -> assertThat(size).isNull())
            .jsonPath("$.items[0].purchasable").isEqualTo(true);
        saveBuyerWallet(5_000L);

        URI location = client.post().uri(ORDERS_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
            .body(Map.of(
                "items", List.of(Map.of("optionId", "30", "quantity", 1, "unitPrice", 5_000)),
                "shippingAddress", ADDRESS))
            .exchange()
            .expectStatus().isCreated()
            .expectBody().returnResult()
            .getResponseHeaders().getLocation();
        assertThat(location).isNotNull();
        Long orderId = Long.valueOf(location.getPath().substring((ORDERS_PATH + "/").length()));
        eventDelivery.deliver(stockDeducted(orderId));

        assertThat(detail(orderId).status()).isEqualTo("PAID");
        client.get().uri(ORDERS_PATH + "/" + orderId)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.items[0].optionName").isEqualTo("색상: 흰색")
            .jsonPath("$.items[0].size").value(size -> assertThat(size).isNull());
        client.get().uri(SELLER_ORDERS_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.seller(SELLER_A_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[0].items[0].optionId").isEqualTo("30")
            .jsonPath("$.content[0].items[0].size").value(size -> assertThat(size).isNull());

        shipAndDeliver(SELLER_A_ID, orderId);
        ItemRow item = itemOfSeller(detail(orderId), SELLER_A_ID);
        client.post().uri(ORDERS_PATH + "/" + orderId + "/items/" + item.orderItemId() + "/confirm")
            .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.size").value(size -> assertThat(size).isNull());

        assertThat(itemOfSeller(detail(orderId), SELLER_A_ID).status()).isEqualTo("CONFIRMED");
        assertThat(single(OrderItemPurchaseConfirmedEvent.class, orderId).data().size()).isNull();
    }

    /**
     * 상품 1(판매자 A, 옵션 1 · 110 · 25,000원), 상품 2(판매자 B, 옵션 2 · 120 · 30,000원), 자녀 1(보호자 1).
     */
    private void publishReplicaEvents() {
        LocalDateTime now = LocalDateTime.now();
        eventDelivery.deliver(new ProductChangedEvent(UUID.randomUUID(), now, new ProductChangedEvent.Data(
            1L, SELLER_A_ID, "상품 A", 1L, ProductStatus.ON_SALE, null, now.minusDays(1), false,
            List.of(new ProductChangedEvent.Option(1L, "색상: 네이비 / 사이즈: 110", "110", 25_000L,
                null, null, false, OptionSaleStatus.ON_SALE)))));
        eventDelivery.deliver(new ProductChangedEvent(UUID.randomUUID(), now, new ProductChangedEvent.Data(
            2L, SELLER_B_ID, "상품 B", 2L, ProductStatus.ON_SALE, null, now.minusDays(1), false,
            List.of(new ProductChangedEvent.Option(2L, "색상: 그레이 / 사이즈: 120", "120", 30_000L,
                null, null, false, OptionSaleStatus.ON_SALE)))));
        eventDelivery.deliver(new ChildChangedEvent(UUID.randomUUID(), now, new ChildChangedEvent.Data(
            CHILD_ID, BUYER_ID, new BigDecimal("115.0"), new BigDecimal("20.0"), Gender.MALE,
            LocalDate.now().minusYears(7), BodyType.NORMAL, PreferredFit.LOOSE, false)));
    }

    private void addToCart(String optionId, String childId, int quantity) {
        Map<String, Object> body = childId == null
            ? Map.of("optionId", optionId, "quantity", quantity)
            : Map.of("optionId", optionId, "childId", childId, "quantity", quantity);
        client.post().uri(CART_ITEMS_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
            .body(body)
            .exchange()
            .expectStatus().isCreated();
    }

    /**
     * 판매자 A 상품(자녀 1) 2개 · 판매자 B 상품 1개.
     */
    private Long placeOrder() {
        URI location = client.post().uri(ORDERS_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
            .body(Map.of(
                "items", List.of(
                    Map.of("optionId", "1", "childId", "1", "quantity", 2, "unitPrice", 25_000),
                    Map.of("optionId", "2", "quantity", 1, "unitPrice", 30_000)),
                "shippingAddress", ADDRESS))
            .exchange()
            .expectStatus().isCreated()
            .expectBody().returnResult()
            .getResponseHeaders().getLocation();
        assertThat(location).isNotNull();
        return Long.valueOf(location.getPath().substring((ORDERS_PATH + "/").length()));
    }

    private void shipAndDeliver(Long sellerId, Long orderId) {
        String sellerOrderId = sellerOrderIdOf(sellerId, orderId);
        client.post().uri(SELLER_ORDERS_PATH + "/" + sellerOrderId + "/ship")
            .headers(headers -> headers.setBearerAuth(tokens.seller(sellerId)))
            .body(Map.of("carrier", "CJ", "trackingNumber", "1234567890"))
            .exchange()
            .expectStatus().isOk();
        client.post().uri(SELLER_ORDERS_PATH + "/" + sellerOrderId + "/deliver")
            .headers(headers -> headers.setBearerAuth(tokens.seller(sellerId)))
            .exchange()
            .expectStatus().isOk();
    }

    private String sellerOrderIdOf(Long sellerId, Long orderId) {
        SellerOrders page = client.get().uri(SELLER_ORDERS_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.seller(sellerId)))
            .exchange()
            .expectStatus().isOk()
            .expectBody(SellerOrders.class)
            .returnResult()
            .getResponseBody();
        assertThat(page).isNotNull();
        return page.content().stream()
            .filter(row -> row.orderId().equals(String.valueOf(orderId)))
            .findFirst()
            .orElseThrow()
            .sellerOrderId();
    }

    private OrderBody detail(Long orderId) {
        OrderBody body = client.get().uri(ORDERS_PATH + "/" + orderId)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody(OrderBody.class)
            .returnResult()
            .getResponseBody();
        assertThat(body).isNotNull();
        return body;
    }

    private List<CartLine> cart() {
        CartBody body = client.get().uri(CART_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody(CartBody.class)
            .returnResult()
            .getResponseBody();
        assertThat(body).isNotNull();
        return body.items();
    }

    private ItemRow itemOfSeller(OrderBody order, Long sellerId) {
        return order.items().stream()
            .filter(item -> item.sellerId().equals(String.valueOf(sellerId)))
            .findFirst()
            .orElseThrow();
    }

    private void saveBuyerWallet(long balance) {
        Wallet wallet = Wallet.create(BUYER_ID);
        wallet.deposit(balance);
        walletRepository.save(wallet);
    }

    private void expire(Long orderId) {
        jdbcTemplate.update("update orders.orders set payment_due_at = ? where id = ?",
            LocalDateTime.now().minusMinutes(1), orderId);
    }

    private StockDeductedEvent stockDeducted(Long orderId) {
        return new StockDeductedEvent(UUID.randomUUID(), LocalDateTime.now(), new StockDeductedEvent.Data(orderId));
    }

    private List<PaymentCompletedEvent> paymentCompleted(Long orderId) {
        return recorder.events(PaymentCompletedEvent.class).stream()
            .filter(event -> event.data().orderId().equals(orderId))
            .toList();
    }

    /**
     * 그 주문의 이벤트가 정확히 하나인지 확인하고 돌려준다.
     */
    private <T extends DomainEvent> T single(Class<T> type, Long orderId) {
        List<T> matched = recorder.events(type).stream()
            .filter(event -> orderIdOf(event).equals(orderId))
            .toList();
        assertThat(matched).hasSize(1);
        return matched.get(0);
    }

    private static Long orderIdOf(DomainEvent event) {
        return switch (event) {
            case OrderCreatedEvent created -> created.data().orderId();
            case OrderPaymentRequestedEvent requested -> requested.data().orderId();
            case OrderCancelledEvent cancelled -> cancelled.data().orderId();
            case OrderItemPurchaseConfirmedEvent confirmed -> confirmed.data().orderId();
            default -> throw new IllegalArgumentException("주문 이벤트가 아닙니다: " + event);
        };
    }

    private static List<String> fieldNames(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    private record OrderBody(
        String orderId, String status, String cancelReason, List<ItemRow> items, List<DeliveryRow> deliveries) {
    }

    private record ItemRow(
        String orderItemId, String sellerId, String status, String deliveredAt, String confirmType) {
    }

    private record DeliveryRow(String sellerId, String status, String deliveredAt) {
    }

    private record SellerOrders(List<SellerOrderRow> content) {
    }

    private record SellerOrderRow(String sellerOrderId, String orderId) {
    }

    private record CartBody(List<CartLine> items) {
    }

    private record CartLine(String optionId, String childId) {
    }
}
