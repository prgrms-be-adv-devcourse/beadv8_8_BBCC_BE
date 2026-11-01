package com.bbcc.kidly.boundedcontext.cart.app;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.cart.app.dto.AddCartItemResult;
import com.bbcc.kidly.boundedcontext.order.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.order.event.CartItemAddedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 장바구니 담기 이벤트(cart.item-added, #654)가 담기와 같은 트랜잭션에서 주문 아웃박스(kidly.order.events)에 저장되는지.
 * 이 컨텍스트는 Kafka 보내기가 꺼져 있어 주문 이벤트처럼 Spring 이벤트 + 아웃박스 저장만 하고 릴레이는 없다.
 * 리스너는 발행한 트랜잭션이 커밋된 뒤에 실행되므로 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
class CartItemAddedOutboxIntegrationTest {

    private static final Long OPTION_ID = 1L;
    private static final Long CHILD_ID = 1L;
    private static final Long PRODUCT_ID = 1L;
    private static final Long STOPPED_OPTION_ID = 2L;

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderFlowEventRecorder recorder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private ChildReplicaRepository childReplicaRepository;

    @BeforeEach
    void setUp() {
        recorder.clear();
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        productReplicaRepository.save(ReplicaProduct.create(
            ReplicaFixture.product(STOPPED_OPTION_ID, 25_000L, "STOPPED", false, OCCURRED_AT)));
        childReplicaRepository.save(ReplicaChild.create(ReplicaFixture.child("115.0", false, OCCURRED_AT)));
    }

    @Test
    @DisplayName("새 줄을 담으면 cart.item-added 한 행: 이벤트 ID · 종류 · 토픽 · 키(회원 ID) · 본문 7칸 + data, 보내지 않은 상태")
    void savesNewLineInSameTransaction() {
        AddCartItemResult added = cartService.addItem(BUYER_ID, OPTION_ID, CHILD_ID, 2);

        CartItemAddedEvent event = recorder.events(CartItemAddedEvent.class).getFirst();
        Map<String, Object> row = jdbcTemplate.queryForMap("select * from orders.order_outbox");
        assertThat(row).containsEntry("event_id", event.eventId())
            .containsEntry("event_type", "cart.item-added")
            .containsEntry("topic", "kidly.order.events")
            .containsEntry("message_key", String.valueOf(BUYER_ID))
            .containsEntry("sent_at", null)
            .containsEntry("attempt_count", 0);
        JsonNode payload = jsonMapper.readTree((String) row.get("payload"));
        assertThat(payload.propertyNames()).containsExactly("eventId", "eventType", "schemaVersion", "occurredAt",
            "producer", "aggregateId", "correlationId", "data");
        assertThat(payload.get("eventId").asString()).isEqualTo(event.eventId().toString());
        assertThat(payload.get("eventType").asString()).isEqualTo("cart.item-added");
        assertThat(payload.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(LocalDateTime.parse(payload.get("occurredAt").asString())).isEqualTo(event.occurredAt());
        assertThat(payload.get("producer").asString()).isEqualTo("order");
        assertThat(payload.get("aggregateId").asString()).isEqualTo(String.valueOf(added.line().cartItemId()));
        assertThat(payload.get("correlationId").asString()).isEqualTo(String.valueOf(BUYER_ID));
        assertThat(payload.get("data").toString()).isEqualTo(jsonMapper.writeValueAsString(
            new CartItemAddedEvent.Data(added.line().cartItemId(), BUYER_ID, CHILD_ID, PRODUCT_ID, OPTION_ID, 2, 2)));
    }

    @Test
    @DisplayName("같은 줄에 다시 담으면 행이 하나 더: quantity = 이번에 담은 수량, cartQuantity = 담은 뒤 줄 수량, 대상은 같은 줄")
    void savesMergedLine() {
        Long cartItemId = cartService.addItem(BUYER_ID, OPTION_ID, null, 2).line().cartItemId();
        cartService.addItem(BUYER_ID, OPTION_ID, null, 3);

        List<CartItemAddedEvent> events = recorder.events(CartItemAddedEvent.class);
        assertThat(events).extracting(CartItemAddedEvent::data).containsExactly(
            new CartItemAddedEvent.Data(cartItemId, BUYER_ID, null, PRODUCT_ID, OPTION_ID, 2, 2),
            new CartItemAddedEvent.Data(cartItemId, BUYER_ID, null, PRODUCT_ID, OPTION_ID, 3, 5));
        assertThat(jdbcTemplate.queryForList("select event_id from orders.order_outbox order by id",
            UUID.class)).containsExactly(events.get(0).eventId(), events.get(1).eventId());
        assertThat(jdbcTemplate.queryForList("select payload from orders.order_outbox order by id", String.class))
            .extracting(payload -> jsonMapper.readTree(payload).get("data").get("cartQuantity").asInt())
            .containsExactly(2, 5);
    }

    @Test
    @DisplayName("담기에 실패하면(자기 상품 · 판매 중 아님 · 수량 초과) 행도 Spring 이벤트도 없다")
    void savesNothingWhenAddFails() {
        cartService.addItem(BUYER_ID, OPTION_ID, null, 99);
        recorder.clear();
        jdbcTemplate.update("delete from orders.order_outbox");

        assertThatThrownBy(() -> cartService.addItem(SELLER_ID, OPTION_ID, null, 1))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> cartService.addItem(BUYER_ID, STOPPED_OPTION_ID, null, 1))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> cartService.addItem(BUYER_ID, OPTION_ID, null, 1))
            .isInstanceOf(BusinessException.class);

        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.order_outbox", Long.class)).isZero();
        assertThat(recorder.events(DomainEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("담기를 감싼 트랜잭션이 롤백되면 장바구니 줄도 아웃박스 행도 Spring 이벤트도 남지 않는다")
    void rollsBackWithBusiness() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            cartService.addItem(BUYER_ID, OPTION_ID, CHILD_ID, 2);
            throw new IllegalStateException("업무 실패");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.cart_item", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.order_outbox", Long.class)).isZero();
        assertThat(recorder.events(DomainEvent.class)).isEmpty();
    }
}
