package com.bbcc.kidly.boundedcontext.delivery.in.web;

import static com.bbcc.kidly.boundedcontext.delivery.in.web.SellerOrderController.SELLER_ORDERS_PATH;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.order.in.web.OrderController.ORDERS_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.OrderEventDelivery;
import com.bbcc.kidly.boundedcontext.order.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.order.PaymentEvents;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.support.FunctionalTest;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import com.bbcc.kidly.shared.order.event.OrderItemCancelledEvent;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 판매자 발송 전 부분 취소 API(#655). 주문은 판매자 2(옵션 1, 2개 · 자녀 1) · 판매자 3(옵션 2, 1개) 몫으로 결제완료다.
 * 주문 모듈 테스트는 스위치(kidly.order.partial-cancel.enabled)를 켜 둔다(꺼짐 거절은 DeliveryServiceTest).
 * SELLER 역할 403은 Gateway가 맡으므로 여기서 보지 않는다.
 */
@FunctionalTest
class SellerOrderCancelApiTest {

    private static final Long OTHER_SELLER_ID = 3L;
    private static final Map<String, String> ADDRESS = Map.of(
        "recipientName", "홍길동", "phone", "010-0000-0000", "zipCode", "12345", "address", "서울시 테스트로 1");

    @Autowired
    private OrderEventDelivery eventDelivery;

    @Autowired
    private OrderFlowEventRecorder recorder;

    @Autowired
    private RestTestClient client;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private ChildReplicaRepository childReplicaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    private Long orderId;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        productReplicaRepository.save(ReplicaProduct.create(new ReplicaProduct.Values(
            2L, 2L, OTHER_SELLER_ID, "상품 B", null, "120", 30_000L, 2L, "ON_SALE", null, false, OCCURRED_AT)));
        childReplicaRepository.save(ReplicaChild.create(ReplicaFixture.child("115.0", false, OCCURRED_AT)));
        orderId = placePaidOrder();
        recorder.clear();
    }

    @Test
    @DisplayName("자기 몫을 취소하면 200 CANCELLED, 그 주문상품만 CANCELLED · 주문은 PAID, "
        + "order-item.cancelled 한 행(키 주문 ID · 본문 7칸 + data)")
    void cancelOwnShare() {
        Long deliveryId = deliveryIdOf(SELLER_ID);

        cancel(SELLER_ID, deliveryId, "OUT_OF_STOCK", "창고 재고 없음")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.sellerOrderId").isEqualTo(String.valueOf(deliveryId))
            .jsonPath("$.status").isEqualTo("CANCELLED");

        client.get().uri(ORDERS_PATH + "/" + orderId)
            .headers(TestMembers.buyer(BUYER_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("PAID")
            .jsonPath("$.items[?(@.optionId == '1')].status").isEqualTo(List.of("CANCELLED"))
            .jsonPath("$.items[?(@.optionId == '2')].status").isEqualTo(List.of("ORDERED"));

        OrderItemCancelledEvent event = recorder.events(OrderItemCancelledEvent.class).getFirst();
        Long orderItemId = orderItemIdOf(1L);
        Long cancellationId = jdbcTemplate.queryForObject(
            "select id from orders.order_item_cancellation where order_id = ? and seller_id = ?", Long.class,
            orderId, SELLER_ID);
        assertThat(event.data()).isEqualTo(new OrderItemCancelledEvent.Data(orderId, BUYER_ID, SELLER_ID,
            cancellationId, "OUT_OF_STOCK", List.of(new OrderItemCancelledEvent.Item(orderItemId, 1L, 2, 50_000L))));
        Map<String, Object> row = jdbcTemplate.queryForMap("select * from orders.order_outbox");
        assertThat(row).containsEntry("event_id", event.eventId())
            .containsEntry("event_type", "order-item.cancelled")
            .containsEntry("topic", "kidly.order.events")
            .containsEntry("message_key", String.valueOf(orderId));
        JsonNode payload = jsonMapper.readTree((String) row.get("payload"));
        assertThat(payload.propertyNames()).containsExactly("eventId", "eventType", "schemaVersion", "occurredAt",
            "producer", "aggregateId", "correlationId", "data");
        assertThat(payload.get("aggregateId").asString()).isEqualTo(String.valueOf(cancellationId));
        assertThat(payload.get("correlationId").asString()).isEqualTo(String.valueOf(orderId));
        assertThat(payload.get("data").toString()).isEqualTo(jsonMapper.writeValueAsString(event.data()));
        assertThat(jdbcTemplate.queryForMap("select reason, memo from orders.order_item_cancellation"))
            .containsEntry("reason", "OUT_OF_STOCK").containsEntry("memo", "창고 재고 없음");
        assertThat(jdbcTemplate.queryForMap(
            "select cancel_reason, cancellation_id, cancelled_at from orders.order_item where id = ?", orderItemId))
            .containsEntry("cancel_reason", "OUT_OF_STOCK").containsEntry("cancellation_id", cancellationId)
            .containsKey("cancelled_at");
    }

    @Test
    @DisplayName("상태 이력: 배송 READY → CANCELLED · 주문상품 ORDERED → CANCELLED, 주체 SELLER · 요청 경로 · 사유")
    void history() {
        Long deliveryId = deliveryIdOf(SELLER_ID);

        cancel(SELLER_ID, deliveryId, "WRONG_PRODUCT_INFO", null).expectStatus().isOk();

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("select target_type, target_id, from_status,"
            + " to_status, reason, actor_type, actor_id, source from orders.order_status_history"
            + " where order_id = ? and to_status = 'CANCELLED' order by id", orderId);
        String source = "POST /api/v1/seller/orders/{sellerOrderId}/cancel";
        assertThat(rows).containsExactlyInAnyOrder(
            historyRow("ORDER_ITEM", orderItemIdOf(1L), "ORDERED", "WRONG_PRODUCT_INFO", source),
            historyRow("DELIVERY", deliveryId, "READY", "WRONG_PRODUCT_INFO", source));
    }

    @Test
    @DisplayName("마지막 판매자 몫까지 취소하면 주문이 CANCELLED(SELLER_CANCELLED)이고, "
        + "나간 이벤트는 order-item.cancelled 두 개뿐 · order.cancelled 0")
    void lastShareCancelsOrderWithoutOrderCancelled() {
        cancel(SELLER_ID, deliveryIdOf(SELLER_ID), "OUT_OF_STOCK", null).expectStatus().isOk();
        cancel(OTHER_SELLER_ID, deliveryIdOf(OTHER_SELLER_ID), "OTHER", null).expectStatus().isOk();

        client.get().uri(ORDERS_PATH + "/" + orderId)
            .headers(TestMembers.buyer(BUYER_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("CANCELLED")
            .jsonPath("$.cancelReason").isEqualTo("SELLER_CANCELLED")
            .jsonPath("$.items[*].status").isEqualTo(List.of("CANCELLED", "CANCELLED"));
        assertThat(recorder.events(OrderCancelledEvent.class)).isEmpty();
        assertThat(recorder.events(OrderItemCancelledEvent.class)).hasSize(2);
        assertThat(jdbcTemplate.queryForList("select event_type from orders.order_outbox order by id", String.class))
            .containsExactly("order-item.cancelled", "order-item.cancelled");
        assertThat(jdbcTemplate.queryForList("select from_status || '>' || to_status || ':' || reason"
                + " from orders.order_status_history where order_id = ? and target_type = 'ORDER'"
                + " and to_status = 'CANCELLED'", String.class, orderId))
            .containsExactly("PAID>CANCELLED:SELLER_CANCELLED");
    }

    @Test
    @DisplayName("같은 요청을 두 번 보내면 두 번째는 409 ALREADY_CANCELLED이고 이벤트 · 취소 기록은 한 번뿐")
    void twiceIsCancelledOnce() {
        Long deliveryId = deliveryIdOf(SELLER_ID);
        cancel(SELLER_ID, deliveryId, "OUT_OF_STOCK", null).expectStatus().isOk();

        cancel(SELLER_ID, deliveryId, "OUT_OF_STOCK", null)
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.title").isEqualTo("이미 취소된 판매 주문입니다");

        assertThat(recorder.events(OrderItemCancelledEvent.class)).hasSize(1);
        assertThat(count("orders.order_outbox")).isEqualTo(1);
        assertThat(count("orders.order_item_cancellation")).isEqualTo(1);
    }

    @Test
    @DisplayName("남의 배송이면 404 DELIVERY_NOT_FOUND(존재를 숨김)이고 아무것도 바뀌지 않는다")
    void othersDeliveryIsNotFound() {
        cancel(OTHER_SELLER_ID, deliveryIdOf(SELLER_ID), "OUT_OF_STOCK", null)
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.title").isEqualTo("판매 주문을 찾을 수 없습니다");

        assertThat(jdbcTemplate.queryForList("select status from orders.delivery", String.class))
            .containsOnly("READY");
        assertThat(count("orders.order_outbox")).isZero();
    }

    @Test
    @DisplayName("발송 · 배송 완료한 몫은 409 ALREADY_SHIPPED이고 이벤트가 없다")
    void shippedIsConflict() {
        Long deliveryId = deliveryIdOf(SELLER_ID);
        client.post().uri(SELLER_ORDERS_PATH + "/" + deliveryId + "/ship")
            .headers(TestMembers.seller(SELLER_ID))
            .body(Map.of("carrier", "CJ", "trackingNumber", "1234567890"))
            .exchange()
            .expectStatus().isOk();

        cancel(SELLER_ID, deliveryId, "OUT_OF_STOCK", null)
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.title").isEqualTo("이미 발송된 주문입니다");
        client.post().uri(SELLER_ORDERS_PATH + "/" + deliveryId + "/deliver")
            .headers(TestMembers.seller(SELLER_ID))
            .exchange()
            .expectStatus().isOk();
        cancel(SELLER_ID, deliveryId, "OUT_OF_STOCK", null).expectStatus().isEqualTo(409);

        assertThat(recorder.events(OrderItemCancelledEvent.class)).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.order_outbox"
            + " where event_type = 'order-item.cancelled'", Long.class)).isZero();
    }

    @Test
    @DisplayName("구매자가 취소한 주문의 배송은 409 ALREADY_CANCELLED, 판매자가 취소한 배송은 발송하면 409 DELIVERY_CANCELLED")
    void cancelledDeliveries() {
        Long deliveryId = deliveryIdOf(SELLER_ID);
        cancel(SELLER_ID, deliveryId, "OUT_OF_STOCK", null).expectStatus().isOk();

        client.post().uri(SELLER_ORDERS_PATH + "/" + deliveryId + "/ship")
            .headers(TestMembers.seller(SELLER_ID))
            .body(Map.of("carrier", "CJ", "trackingNumber", "1234567890"))
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.title").isEqualTo("구매자가 취소한 주문입니다");

        client.post().uri(ORDERS_PATH + "/" + orderId + "/cancel")
            .headers(TestMembers.buyer(BUYER_ID))
            .exchange()
            .expectStatus().isOk();
        cancel(OTHER_SELLER_ID, deliveryIdOf(OTHER_SELLER_ID), "OTHER", null)
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.title").isEqualTo("이미 취소된 판매 주문입니다");
    }

    @Test
    @DisplayName("일부 몫이 취소된 뒤 구매자 전체 취소는 그대로: 주문 CANCELLED(BUYER_CANCELLED) · order.cancelled 한 번")
    void buyerCancelAfterPartial() {
        cancel(SELLER_ID, deliveryIdOf(SELLER_ID), "OUT_OF_STOCK", null).expectStatus().isOk();

        client.post().uri(ORDERS_PATH + "/" + orderId + "/cancel")
            .headers(TestMembers.buyer(BUYER_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("CANCELLED")
            .jsonPath("$.cancelReason").isEqualTo("BUYER_CANCELLED");

        assertThat(recorder.events(OrderCancelledEvent.class)).singleElement()
            .satisfies(event -> assertThat(event.data().reason()).isEqualTo("BUYER_CANCELLED"));
        assertThat(jdbcTemplate.queryForList("select event_type from orders.order_outbox order by id", String.class))
            .containsExactly("order-item.cancelled", "order.cancelled");
    }

    @Test
    @DisplayName("타임라인: 취소된 몫(배송 건 · 주문상품)은 NOT_APPLICABLE이고 단계 진행에서 빠져, 남은 몫이 배송 완료면 DELIVERED까지 DONE")
    void timelineSkipsCancelledShare() {
        Long cancelledDeliveryId = deliveryIdOf(SELLER_ID);
        Long otherDeliveryId = deliveryIdOf(OTHER_SELLER_ID);
        cancel(SELLER_ID, cancelledDeliveryId, "OUT_OF_STOCK", null).expectStatus().isOk();
        client.post().uri(SELLER_ORDERS_PATH + "/" + otherDeliveryId + "/ship")
            .headers(TestMembers.seller(OTHER_SELLER_ID))
            .body(Map.of("carrier", "CJ", "trackingNumber", "1234567890"))
            .exchange()
            .expectStatus().isOk();
        client.post().uri(SELLER_ORDERS_PATH + "/" + otherDeliveryId + "/deliver")
            .headers(TestMembers.seller(OTHER_SELLER_ID))
            .exchange()
            .expectStatus().isOk();

        client.get().uri(ORDERS_PATH + "/" + orderId + "/timeline")
            .headers(TestMembers.buyer(BUYER_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("PAID")
            .jsonPath("$.currentStep").isEqualTo("DELIVERED")
            .jsonPath("$.steps[?(@.step == 'SHIPPED')].status").isEqualTo(List.of("DONE"))
            .jsonPath("$.steps[?(@.step == 'DELIVERED')].status").isEqualTo(List.of("DONE"))
            .jsonPath("$.steps[?(@.step == 'DELIVERED')].details[?(@.sellerOrderId == '" + cancelledDeliveryId
                + "')].status").isEqualTo(List.of("NOT_APPLICABLE"))
            .jsonPath("$.steps[?(@.step == 'CONFIRMED')].status").isEqualTo(List.of("WAITING"))
            .jsonPath("$.steps[?(@.step == 'CONFIRMED')].details[?(@.orderItemId == '" + orderItemIdOf(1L)
                + "')].status").isEqualTo(List.of("NOT_APPLICABLE"));
    }

    @Test
    @DisplayName("모르는 사유는 400 INVALID_CANCEL_REASON, 사유 없음 · 메모 501자는 400, 메모 500자는 200")
    void validation() {
        Long deliveryId = deliveryIdOf(SELLER_ID);

        cancel(SELLER_ID, deliveryId, "SOLD_OUT", null)
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.title").isEqualTo("지원하지 않는 취소 사유입니다");
        cancel(SELLER_ID, deliveryId, " ", null).expectStatus().isBadRequest();
        cancel(SELLER_ID, deliveryId, "OTHER", "가".repeat(501)).expectStatus().isBadRequest();
        assertThat(count("orders.order_outbox")).isZero();

        cancel(SELLER_ID, deliveryId, "OTHER", "가".repeat(500)).expectStatus().isOk();
    }

    @Test
    @DisplayName("로그인하지 않으면 401")
    void unauthorized() {
        client.post().uri(SELLER_ORDERS_PATH + "/" + deliveryIdOf(SELLER_ID) + "/cancel")
            .body(Map.of("reason", "OTHER"))
            .exchange()
            .expectStatus().isUnauthorized();
    }

    private RestTestClient.ResponseSpec cancel(Long sellerId, Long deliveryId, String reason, String memo) {
        Map<String, Object> body = new HashMap<>();
        body.put("reason", reason);
        if (memo != null) {
            body.put("memo", memo);
        }
        return client.post().uri(SELLER_ORDERS_PATH + "/" + deliveryId + "/cancel")
            .headers(TestMembers.seller(sellerId))
            .body(body)
            .exchange();
    }

    private Map<String, Object> historyRow(String targetType, Long targetId, String from, String reason,
        String source) {
        Map<String, Object> row = new HashMap<>();
        row.put("target_type", targetType);
        row.put("target_id", targetId);
        row.put("from_status", from);
        row.put("to_status", "CANCELLED");
        row.put("reason", reason);
        row.put("actor_type", "SELLER");
        row.put("actor_id", SELLER_ID);
        row.put("source", source);
        return row;
    }

    private Long deliveryIdOf(Long sellerId) {
        return jdbcTemplate.queryForObject("select id from orders.delivery where order_id = ? and seller_id = ?",
            Long.class, orderId, sellerId);
    }

    private Long orderItemIdOf(Long optionId) {
        return jdbcTemplate.queryForObject("select id from orders.order_item where order_id = ? and option_id = ?",
            Long.class, orderId, optionId);
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
    }

    private Long placePaidOrder() {
        URI location = client.post().uri(ORDERS_PATH)
            .headers(TestMembers.buyer(BUYER_ID))
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
        Long id = Long.valueOf(location.getPath().substring((ORDERS_PATH + "/").length()));
        eventDelivery.deliver(new StockDeductedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new StockDeductedEvent.Data(id)));
        // 주문 모듈 테스트에는 결제 도메인이 없으므로 결제 완료됨을 직접 넣는다(#616)
        eventDelivery.deliver(PaymentEvents.completed(orderService.getOrder(BUYER_ID, id), BUYER_ID));
        // 주문 · 결제 흐름의 아웃박스 행은 지워 이 테스트가 낸 행만 본다
        jdbcTemplate.update("delete from orders.order_outbox");
        return id;
    }
}
