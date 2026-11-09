package com.bbcc.kidly.boundedcontext.order.in.event;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.cart.CartOwners;
import com.bbcc.kidly.boundedcontext.cart.app.CartService;
import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.order.in.config.OrderKafkaListenerConfig;
import com.bbcc.kidly.boundedcontext.support.AsyncWait;
import com.bbcc.kidly.boundedcontext.support.KafkaIntegrationTest;
import com.bbcc.kidly.boundedcontext.wish.app.WishRequestService;
import com.bbcc.kidly.boundedcontext.wish.in.event.WishCartEventKafkaListener;
import com.bbcc.kidly.boundedcontext.wish.out.actor.ChildWishActor;
import com.bbcc.kidly.shared.order.event.CartItemPurchasedEvent;
import com.bbcc.kidly.shared.order.event.OrderCreatedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 조르기가 주문 자기 토픽(kidly.order.events)의 장바구니 구매 사실(cart.item-purchased · 예약 ID)을 Kafka로 받아 결제 완료로
 * 바꾼다(#999, 참모 결정 ①). 리스너는 주문 공용 수신 기반(그룹 kidly.order · 주문 팩토리 · 주문 DLT)을 같은 값으로 쓴다.
 * 테스트가 보내는 쪽 규칙대로 메시지를 직접 넣고, 리스너는 이 테스트 안에서만 켠다.
 */
@KafkaIntegrationTest
class WishCartEventKafkaIntegrationTest {

    private static final String TOPIC = "kidly.order.events";
    private static final Long CHILD_ID = 1L;

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private OrderKafkaListenerTestGuard guard;

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

    private OrderKafkaTestClient kafkaClient;
    private MessageListenerContainer listener;

    @BeforeEach
    void startListener() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        childReplicaRepository.save(ReplicaChild.create(ReplicaFixture.child("115.0", false, OCCURRED_AT)));
        kafkaClient = new OrderKafkaTestClient(kafka, jsonMapper);
        listener = kafkaClient.start(guard, WishCartEventKafkaListener.ID, TOPIC);
    }

    @AfterEach
    void stopListener() {
        listener.stop();
    }

    @Test
    @DisplayName("조르기 리스너는 주문 공용 수신 기반(토픽 · 그룹 · 팩토리)과 같은 값을 쓴다")
    void sameReceiveBase() {
        assertThat(WishCartEventKafkaListener.TOPIC).isEqualTo(OrderKafkaListenerConfig.ORDER_EVENTS);
        assertThat(WishCartEventKafkaListener.GROUP).isEqualTo(OrderKafkaListenerConfig.GROUP);
        assertThat(WishCartEventKafkaListener.FACTORY).isEqualTo(OrderKafkaListenerConfig.FACTORY);
    }

    @Test
    @DisplayName("구매 사실을 Kafka로 받으면 조르기가 결제 완료가 되고, 같은 메시지를 다시 받아도 1회분이며 DLT로 보내지 않는다")
    void purchasedOverKafka() {
        Long line = cartService.addItem(CartOwners.child(CHILD_ID, BUYER_ID), 1L, null, 1).line().cartItemId();
        Long requestId = wishRequestService.send(new ChildWishActor(CHILD_ID, BUYER_ID), List.of(line), null)
            .requestId();
        Long orderId = ThreadLocalRandom.current().nextLong(1_000_000, Long.MAX_VALUE);
        Long orderItemId = orderId + 1;
        var event = new CartItemPurchasedEvent(UUID.randomUUID(), LocalDateTime.now(), new CartItemPurchasedEvent.Data(
            line, "CHILD", CHILD_ID, BUYER_ID, 1L, CHILD_ID, 1, orderId, orderItemId, "wish-" + requestId));

        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, event));
        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, event));

        assertThat(jdbcTemplate.queryForObject("select status from orders.wish_request where id = ?", String.class,
            requestId)).isEqualTo("PAID");
        assertThat(jdbcTemplate.queryForList("select order_id, order_item_id, cart_item_id from orders.wish_purchase"
            + " where request_id = ?", requestId)).singleElement()
            .satisfies(row -> assertThat(row).containsEntry("order_id", orderId)
                .containsEntry("order_item_id", orderItemId)
                .containsEntry("cart_item_id", line));
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.order_outbox where event_type = 'wish.paid'"
            + " and payload::jsonb ->> 'aggregateId' = ?", Long.class, String.valueOf(requestId))).isOne();
        assertThat(kafkaClient.deadLetters(TOPIC, event.eventId().toString())).isEmpty();
    }

    @Test
    @DisplayName("다른 종류(order.created · 사용자 빼기 cart.item-removed 등)는 본문을 읽지 않고, 예약 없는 구매 사실은 조르기와 무관해 건너뛴다(DLT 없음)")
    void skipsOthers() {
        var created = new OrderCreatedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new OrderCreatedEvent.Data(ThreadLocalRandom.current().nextLong(1_000_000, Long.MAX_VALUE), BUYER_ID,
                List.of()));
        String garbageId = OrderKafkaTestClient.newEventId();
        String removedId = OrderKafkaTestClient.newEventId();
        var notReserved = new CartItemPurchasedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new CartItemPurchasedEvent.Data(1L, "MEMBER", BUYER_ID, BUYER_ID, 1L, null, 1, 7L, 8L, null));

        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, created));
        // 다른 종류는 본문을 읽지 않으므로 읽을 수 없는 본문이어도 실패하지 않는다
        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, "1", "order.paid", garbageId, "{not json"));
        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, "1", "cart.item-removed", removedId, "{not json"));
        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, notReserved));

        assertThat(kafkaClient.deadLetters(TOPIC, created.eventId().toString())).isEmpty();
        assertThat(kafkaClient.deadLetters(TOPIC, garbageId)).isEmpty();
        assertThat(kafkaClient.deadLetters(TOPIC, removedId)).isEmpty();
        assertThat(kafkaClient.deadLetters(TOPIC, notReserved.eventId().toString())).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.wish_purchase", Long.class)).isZero();
    }

    @Test
    @DisplayName("읽을 수 없는 구매 사실은 재시도 없이 주문 DLT(kidly.order.events.order.dlt)로 간다")
    void unreadableToDeadLetter() {
        String eventId = OrderKafkaTestClient.newEventId();

        kafkaClient.send(TOPIC, "1", "cart.item-purchased", eventId, "{not json");

        AsyncWait.until(OrderKafkaTestClient.TIMEOUT, () -> !kafkaClient.deadLetters(TOPIC, eventId).isEmpty());
        assertThat(kafkaClient.deadLetters(TOPIC, eventId)).singleElement()
            .satisfies(deadLetter -> assertThat(deadLetter.topic())
                .isEqualTo(OrderKafkaListenerConfig.ORDER_EVENTS_DLT));
    }
}
