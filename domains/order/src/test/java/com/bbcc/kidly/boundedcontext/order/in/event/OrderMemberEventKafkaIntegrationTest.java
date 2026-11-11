package com.bbcc.kidly.boundedcontext.order.in.event;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.cart.CartOwners;
import com.bbcc.kidly.boundedcontext.cart.app.CartService;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.support.KafkaIntegrationTest;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
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
 * 주문이 kidly.member.events의 member.withdrawn을 Kafka로 받는다(#1001). 테스트가 보내는 쪽 규칙대로 메시지를 직접 넣고, 리스너는 이
 * 테스트 안에서만 켠다. 회원 ID는 테스트마다 새로 만든다.
 */
@KafkaIntegrationTest
class OrderMemberEventKafkaIntegrationTest {

    private static final String TOPIC = "kidly.member.events";

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private OrderKafkaListenerTestGuard guard;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Long memberId = ThreadLocalRandom.current().nextLong(1_000_000, 9_000_000_000L);
    private OrderKafkaTestClient kafkaClient;
    private MessageListenerContainer listener;

    @BeforeEach
    void startListener() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        kafkaClient = new OrderKafkaTestClient(kafka, jsonMapper);
        listener = kafkaClient.start(guard, MemberEventKafkaListener.ID, TOPIC);
    }

    @AfterEach
    void stopListener() {
        if (listener != null) {
            listener.stop();
        }
    }


    @Test
    @DisplayName("member.withdrawn을 Kafka로 받으면 결제대기 주문이 취소되고(order.cancelled 1건) 회원 장바구니가 빈다. 다시 받아도 같고 DLT는 없다")
    void withdrawnOverKafka() {
        cartService.addItem(CartOwners.member(memberId), 1L, null, 1);
        Long orderId = orderService.place(memberId, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, null, 1, 25_000L)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시"))).orderId();
        var event = new MemberWithdrawnEvent(UUID.randomUUID(), LocalDateTime.now(),
            new MemberWithdrawnEvent.Data(memberId));

        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, event));
        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, event));

        assertThat(jdbcTemplate.queryForObject("select status from orders.orders where id = ?", String.class,
            orderId)).isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.order_outbox where event_type = "
            + "'order.cancelled' and message_key = ?", Long.class, String.valueOf(orderId))).isOne();
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.cart_item where member_id = ?",
            Long.class, memberId)).isZero();
        assertThat(kafkaClient.deadLetters(TOPIC, event.eventId().toString())).isEmpty();
    }

    @Test
    @DisplayName("다른 회원 이벤트(member.registered 등)는 읽지 않고 건너뛴다(DLT 없음)")
    void skipsOtherMemberEvents() {
        String eventId = OrderKafkaTestClient.newEventId();

        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, String.valueOf(memberId), "member.registered", eventId,
            "{not json"));

        assertThat(kafkaClient.deadLetters(TOPIC, eventId)).isEmpty();
    }
}
