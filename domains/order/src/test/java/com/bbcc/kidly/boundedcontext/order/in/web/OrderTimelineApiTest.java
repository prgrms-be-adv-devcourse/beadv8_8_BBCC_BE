package com.bbcc.kidly.boundedcontext.order.in.web;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.order.in.web.OrderController.ORDERS_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.MarkPaidCommands;
import com.bbcc.kidly.boundedcontext.delivery.app.DeliveryService;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.StockDeductionFailedCommand;
import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.SellerReplicaRepository;
import com.bbcc.kidly.boundedcontext.delivery.domain.Delivery;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaSeller;
import com.bbcc.kidly.boundedcontext.order.support.FunctionalTest;
import com.bbcc.kidly.boundedcontext.order.support.TestTokens;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 주문 처리 타임라인 API(#352). 주문은 API로 만들고, 다른 도메인 이벤트 대신 주문 서비스를 바로 불러 단계를 진행한다.
 * 주문상품 2개: 옵션 1(판매자 2, 상호명 있음) · 옵션 2(판매자 3, 판매자 복사본 없음).
 */
@FunctionalTest
class OrderTimelineApiTest {

    private static final Long OTHER_SELLER_ID = 3L;
    private static final Long OTHER_BUYER_ID = 9L;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Map<String, String> ADDRESS = Map.of(
        "recipientName", "홍길동", "phone", "010-0000-0000", "zipCode", "12345", "address", "서울시 테스트로 1");

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Autowired
    private OrderService orderService;

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private SellerReplicaRepository sellerReplicaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        productReplicaRepository.save(ReplicaProduct.create(new ReplicaProduct.Values(
            2L, 2L, OTHER_SELLER_ID, "상품 B", null, "120", 30_000L, 2L, "ON_SALE", null, false, OCCURRED_AT)));
        sellerReplicaRepository.save(ReplicaSeller.create(
            new ReplicaSeller.Values(SELLER_ID, "키들리상점", "ACTIVE", OCCURRED_AT)));
    }

    @Test
    @DisplayName("응답 모양(예시 ①): 키 이름 · 순서, ID 문자열, 값 없는 키는 null, details는 단계에 따라 배송 건 · 주문상품 · []")
    void responseShapeOfPartiallyShipped() {
        Long orderId = place();
        payAndCreateDeliveries(orderId);
        List<Delivery> deliveries = deliveryRepository.findAllByOrderIdOrderByIdAsc(orderId);
        deliveryService.ship(SELLER_ID, deliveries.get(0).getId(), "CJ", "1234567890");

        JsonNode body = timeline(orderId);

        assertThat(names(body)).containsExactly(
            "orderId", "status", "currentStep", "cancelReason", "paymentDueAt", "steps");
        assertThat(body.get("orderId").isString()).isTrue();
        assertThat(body.get("orderId").asString()).isEqualTo(String.valueOf(orderId));
        assertThat(body.get("status").asString()).isEqualTo("PAID");
        assertThat(body.get("currentStep").asString()).isEqualTo("SHIPPED");
        assertThat(body.get("cancelReason").isNull()).isTrue();
        assertThat(body.get("paymentDueAt").isNull()).isTrue();
        assertThat(stepNames(body)).containsExactly(
            "CREATED", "PAYMENT_REQUESTED", "PAID", "SHIPPED", "DELIVERED", "CONFIRMED");
        for (JsonNode step : body.get("steps")) {
            assertThat(names(step)).containsExactly("step", "status", "at", "approximate", "details");
            assertThat(step.get("approximate").asBoolean()).isFalse();
            assertThat(step.get("details").isArray()).isTrue();
        }

        JsonNode created = step(body, "CREATED");
        assertThat(created.get("status").asString()).isEqualTo("DONE");
        assertThat(created.get("at").isNull()).isFalse();
        assertThat(created.get("details").isArray()).isTrue();
        assertThat(created.get("details").isEmpty()).isTrue();

        JsonNode shipped = step(body, "SHIPPED");
        assertThat(shipped.get("status").asString()).isEqualTo("PARTIAL");
        assertThat(shipped.get("at").isNull()).isTrue();
        JsonNode first = shipped.get("details").get(0);
        JsonNode second = shipped.get("details").get(1);
        assertThat(shipped.get("details").size()).isEqualTo(2);
        assertThat(names(first)).containsExactly("sellerOrderId", "sellerId", "sellerName", "status", "at");
        assertThat(first.get("sellerOrderId").asString()).isEqualTo(String.valueOf(deliveries.get(0).getId()));
        assertThat(first.get("sellerId").asString()).isEqualTo(String.valueOf(SELLER_ID));
        assertThat(first.get("sellerName").asString()).isEqualTo("키들리상점");
        assertThat(first.get("status").asString()).isEqualTo("DONE");
        assertThat(first.get("at").isNull()).isFalse();
        assertThat(second.get("sellerId").asString()).isEqualTo(String.valueOf(OTHER_SELLER_ID));
        assertThat(second.get("sellerName").isNull()).isTrue();
        assertThat(second.get("status").asString()).isEqualTo("WAITING");
        assertThat(second.get("at").isNull()).isTrue();

        JsonNode confirmed = step(body, "CONFIRMED");
        assertThat(confirmed.get("status").asString()).isEqualTo("WAITING");
        JsonNode item = confirmed.get("details").get(0);
        assertThat(confirmed.get("details").size()).isEqualTo(2);
        assertThat(names(item)).containsExactly("orderItemId", "productName", "status", "at", "confirmType");
        assertThat(item.get("orderItemId").isString()).isTrue();
        assertThat(item.get("status").asString()).isEqualTo("WAITING");
        assertThat(item.get("at").isNull()).isTrue();
        assertThat(item.get("confirmType").isNull()).isTrue();
    }

    @Test
    @DisplayName("응답 모양(예시 ②): 결제 요청 전 재고 부족 취소면 PAID 없이 CANCELLED(approximate true), 나머지 NOT_APPLICABLE · []")
    void responseShapeOfOutOfStock() {
        Long orderId = place();
        OrderDetail detail = orderService.getOrder(BUYER_ID, orderId);
        orderService.cancelForStockDeductionFailure(new StockDeductionFailedCommand(orderId, List.of(
            new StockDeductionFailedCommand.Item(detail.items().get(0).orderItemId(), "OUT_OF_STOCK"))));

        JsonNode body = timeline(orderId);

        assertThat(body.get("status").asString()).isEqualTo("CANCELLED");
        assertThat(body.get("currentStep").asString()).isEqualTo("CANCELLED");
        assertThat(body.get("cancelReason").asString()).isEqualTo("OUT_OF_STOCK");
        assertThat(body.get("paymentDueAt").isNull()).isTrue();
        assertThat(stepNames(body)).containsExactly(
            "CREATED", "PAYMENT_REQUESTED", "CANCELLED", "SHIPPED", "DELIVERED", "CONFIRMED");
        assertThat(step(body, "PAYMENT_REQUESTED").get("status").asString()).isEqualTo("NOT_APPLICABLE");
        for (String done : List.of("CREATED", "PAYMENT_REQUESTED")) {
            assertThat(step(body, done).get("approximate").asBoolean()).as(done).isFalse();
        }
        JsonNode cancelled = step(body, "CANCELLED");
        assertThat(cancelled.get("status").asString()).isEqualTo("DONE");
        assertThat(cancelled.get("at").isNull()).isFalse();
        assertThat(cancelled.get("approximate").asBoolean()).isTrue();
        for (String rest : List.of("SHIPPED", "DELIVERED", "CONFIRMED")) {
            JsonNode step = step(body, rest);
            assertThat(step.get("status").asString()).as(rest).isEqualTo("NOT_APPLICABLE");
            assertThat(step.get("at").isNull()).as(rest).isTrue();
            assertThat(step.get("approximate").asBoolean()).as(rest).isFalse();
            assertThat(step.get("details").isArray() && step.get("details").isEmpty()).as(rest).isTrue();
        }
    }

    @Test
    @DisplayName("결제대기 → 결제 요청 → 결제완료 → 배송 완료 → 일부 확정(DIRECT) → 모두 확정(AUTO) 순으로 현재 단계가 나아간다")
    void progressesThroughSteps() {
        Long orderId = place();
        JsonNode pending = timeline(orderId);
        assertThat(pending.get("currentStep").asString()).isEqualTo("CREATED");
        assertThat(pending.get("paymentDueAt").isNull()).isFalse();
        assertThat(step(pending, "PAYMENT_REQUESTED").get("status").asString()).isEqualTo("WAITING");
        assertThat(step(pending, "PAID").get("status").asString()).isEqualTo("WAITING");
        assertThat(step(pending, "SHIPPED").get("details").isEmpty()).isTrue();
        assertThat(step(pending, "CONFIRMED").get("details").size()).isEqualTo(2);

        markPaymentRequested(orderId);
        JsonNode requested = timeline(orderId);
        assertThat(requested.get("currentStep").asString()).isEqualTo("PAYMENT_REQUESTED");
        assertThat(step(requested, "PAYMENT_REQUESTED").get("status").asString()).isEqualTo("DONE");

        payAndCreateDeliveries(orderId);
        for (Delivery delivery : deliveryRepository.findAllByOrderIdOrderByIdAsc(orderId)) {
            deliveryService.ship(delivery.getSellerId(), delivery.getId(), "CJ", "1234567890");
            deliveryService.complete(delivery.getSellerId(), delivery.getId());
        }
        // 두 판매자의 배송 완료 시각이 같아도(동률) 단계 at은 정확히 그 시각
        LocalDateTime sameDeliveredAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).minusHours(1);
        jdbcTemplate.update("update orders.delivery set delivered_at = ? where order_id = ?", sameDeliveredAt, orderId);
        JsonNode delivered = timeline(orderId);
        assertThat(delivered.get("currentStep").asString()).isEqualTo("DELIVERED");
        assertThat(delivered.get("paymentDueAt").isNull()).isTrue();
        assertThat(step(delivered, "SHIPPED").get("status").asString()).isEqualTo("DONE");
        assertThat(step(delivered, "DELIVERED").get("status").asString()).isEqualTo("DONE");
        assertThat(LocalDateTime.parse(step(delivered, "DELIVERED").get("at").asString())).isEqualTo(sameDeliveredAt);
        assertThat(step(delivered, "CONFIRMED").get("status").asString()).isEqualTo("WAITING");

        List<OrderDetail.Item> items = orderService.getOrder(BUYER_ID, orderId).items();
        client.post().uri(ORDERS_PATH + "/" + orderId + "/items/" + items.get(0).orderItemId() + "/confirm")
            .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
            .exchange()
            .expectStatus().isOk();
        JsonNode partlyConfirmed = timeline(orderId);
        assertThat(partlyConfirmed.get("currentStep").asString()).isEqualTo("CONFIRMED");
        assertThat(step(partlyConfirmed, "CONFIRMED").get("status").asString()).isEqualTo("PARTIAL");
        assertThat(step(partlyConfirmed, "CONFIRMED").get("details").get(0).get("confirmType").asString())
            .isEqualTo("DIRECT");

        // 배송 완료 시각은 처음 값을 유지하므로, 자동 확정 기간(15일)이 지난 것으로 시각만 당긴다
        jdbcTemplate.update("update orders.order_item set delivered_at = ? where id = ?",
            LocalDateTime.now().minusDays(16), items.get(1).orderItemId());
        orderService.confirmAutomatically(orderId, items.get(1).orderItemId());
        JsonNode confirmed = timeline(orderId);
        JsonNode confirmedStep = step(confirmed, "CONFIRMED");
        assertThat(confirmedStep.get("status").asString()).isEqualTo("DONE");
        assertThat(confirmedStep.get("at").isNull()).isFalse();
        assertThat(confirmedStep.get("details").get(0).get("confirmType").asString()).isEqualTo("DIRECT");
        assertThat(confirmedStep.get("details").get(1).get("confirmType").asString()).isEqualTo("AUTO");
    }

    @Test
    @DisplayName("결제 요청 뒤 결제 실패 · 결제 마감 만료로 취소되면 PAYMENT_REQUESTED는 DONE, PAID 없이 CANCELLED가 현재 단계")
    void cancelledAfterPaymentRequest() {
        Long failed = place();
        markPaymentRequested(failed);
        orderService.cancelForPaymentFailure(failed);
        Long expired = place();
        markPaymentRequested(expired);
        jdbcTemplate.update("update orders.orders set payment_due_at = ? where id = ?",
            LocalDateTime.now().minusMinutes(1), expired);
        orderService.cancelForPaymentExpiry(expired);

        JsonNode failedBody = timeline(failed);
        JsonNode expiredBody = timeline(expired);

        assertThat(failedBody.get("cancelReason").asString()).isEqualTo("PAYMENT_FAILED");
        assertThat(expiredBody.get("cancelReason").asString()).isEqualTo("PAYMENT_EXPIRED");
        for (JsonNode body : List.of(failedBody, expiredBody)) {
            assertThat(stepNames(body)).containsExactly(
                "CREATED", "PAYMENT_REQUESTED", "CANCELLED", "SHIPPED", "DELIVERED", "CONFIRMED");
            assertThat(step(body, "PAYMENT_REQUESTED").get("status").asString()).isEqualTo("DONE");
            assertThat(body.get("currentStep").asString()).isEqualTo("CANCELLED");
            assertThat(step(body, "CANCELLED").get("approximate").asBoolean()).isTrue();
        }
    }

    @Test
    @DisplayName("남의 주문과 없는 주문은 똑같이 404 ORDER_NOT_FOUND(존재 여부를 드러내지 않음)")
    void othersAndMissingOrderAreSameNotFound() {
        Long orderId = place();

        JsonNode others = notFound(orderId, OTHER_BUYER_ID);
        JsonNode missing = notFound(orderId + 999, BUYER_ID);

        for (JsonNode body : List.of(others, missing)) {
            assertThat(body.get("status").asInt()).isEqualTo(404);
            assertThat(body.get("title").asString()).isEqualTo("주문을 찾을 수 없습니다");
            assertThat(body.get("detail").asString()).isEqualTo("없는 주문입니다. 주문 목록에서 다시 선택해 주세요.");
        }
        assertThat(others.get("type")).isEqualTo(missing.get("type"));
        assertThat(names(others)).isEqualTo(names(missing));
    }

    @Test
    @DisplayName("토큰이 없으면 401")
    void unauthorizedWithoutToken() {
        Long orderId = place();

        client.get().uri(ORDERS_PATH + "/" + orderId + "/timeline")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    private Long place() {
        URI location = client.post().uri(ORDERS_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
            .body(Map.of("items", List.of(
                    Map.of("optionId", "1", "quantity", 1, "unitPrice", 25_000),
                    Map.of("optionId", "2", "quantity", 1, "unitPrice", 30_000)),
                "shippingAddress", ADDRESS))
            .exchange()
            .expectStatus().isCreated()
            .expectBody().returnResult()
            .getResponseHeaders().getLocation();
        assertThat(location).isNotNull();
        return Long.valueOf(location.getPath().substring((ORDERS_PATH + "/").length()));
    }

    /**
     * 결제 요청 시각만 기록해 결제 요청 뒤 · 결제대기 상태를 만든다. 주문 모듈 테스트에는 결제 도메인이 없으므로
     * 결제 결과(결제완료 · 결제 실패)는 테스트가 직접 정한다.
     */
    private void markPaymentRequested(Long orderId) {
        jdbcTemplate.update("update orders.orders set payment_requested_at = ? where id = ?",
            LocalDateTime.now(), orderId);
    }

    private void payAndCreateDeliveries(Long orderId) {
        markPaymentRequested(orderId);
        orderService.markPaid(MarkPaidCommands.matching(orderService.getOrder(BUYER_ID, orderId), BUYER_ID, 500L));
        deliveryService.createFor(orderId);
    }

    private JsonNode timeline(Long orderId) {
        String body = client.get().uri(ORDERS_PATH + "/" + orderId + "/timeline")
            .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
        return JSON.readTree(body);
    }

    private JsonNode notFound(Long orderId, Long memberId) {
        String body = client.get().uri(ORDERS_PATH + "/" + orderId + "/timeline")
            .headers(headers -> headers.setBearerAuth(tokens.buyer(memberId)))
            .exchange()
            .expectStatus().isNotFound()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
        return JSON.readTree(body);
    }

    private static JsonNode step(JsonNode body, String name) {
        for (JsonNode step : body.get("steps")) {
            if (step.get("step").asString().equals(name)) {
                return step;
            }
        }
        throw new AssertionError("단계 없음: " + name);
    }

    private static List<String> stepNames(JsonNode body) {
        List<String> names = new ArrayList<>();
        body.get("steps").forEach(step -> names.add(step.get("step").asString()));
        return names;
    }

    private static List<String> names(JsonNode node) {
        return new ArrayList<>(node.propertyNames());
    }
}
