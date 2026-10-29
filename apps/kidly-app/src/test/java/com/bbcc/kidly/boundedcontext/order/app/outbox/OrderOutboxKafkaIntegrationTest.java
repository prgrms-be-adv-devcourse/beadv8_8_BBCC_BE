package com.bbcc.kidly.boundedcontext.order.app.outbox;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.OrderDeliveredAtSetter;
import com.bbcc.kidly.boundedcontext.order.OrderEventDelivery;
import com.bbcc.kidly.boundedcontext.order.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.outbox.dto.OutboxRelayResult;
import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderOutboxRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.outbox.OrderOutboxMessage;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import com.bbcc.kidly.shared.order.event.OrderCreatedEvent;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import com.bbcc.kidly.shared.order.event.OrderPaymentRequestedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 주문 이벤트가 아웃박스 → 릴레이 → Kafka(kidly.order.events)로 도착하는지(#540). 테스트용 Kafka 컨테이너를 쓴다.
 * 앱은 Boot 연결 정보(@ServiceConnection)로 보내고, 테스트는 컨테이너 주소로 직접 읽는다: 연결이 컨테이너를 가리키지 않으면 도착하지 않는다.
 * 토픽은 컨테이너 수명 동안 남고 DB는 테스트마다 비워져 주문 ID가 겹치므로, 메시지는 이벤트 ID로 골라 읽는다.
 */
@IntegrationTest
class OrderOutboxKafkaIntegrationTest {

    private static final String TOPIC = "kidly.order.events";
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderOutboxRelayService relayService;

    @Autowired
    private OrderOutboxRepository outboxRepository;

    @Autowired
    private OrderEventDelivery eventDelivery;

    @Autowired
    private OrderDeliveredAtSetter deliveredAtSetter;

    @Autowired
    private OrderFlowEventRecorder recorder;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private ChildReplicaRepository childReplicaRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        recorder.clear();
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        childReplicaRepository.save(ReplicaChild.create(ReplicaFixture.child("115.0", false, OCCURRED_AT)));
    }

    @Test
    @DisplayName("실제 주문 흐름의 4종이 kidly.order.events(파티션 3)에 도착한다: 키 = 주문 ID, 헤더 eventType · eventId, 본문 7칸 + data")
    void deliversFourEventTypes() {
        // 지갑에 주문 하나 값만 넣어, 첫 주문은 결제 → 배송 완료 → 구매확정, 둘째 주문은 잔액 부족으로 결제 실패 → 취소
        Wallet wallet = Wallet.create(BUYER_ID);
        wallet.deposit(50_000L);
        walletRepository.save(wallet);
        OrderDetail confirmed = place();
        eventDelivery.deliver(stockDeducted(confirmed.orderId()));
        deliveredAtSetter.recordDelivered(confirmed.orderId(), SELLER_ID, LocalDateTime.now());
        orderService.confirmByBuyer(BUYER_ID, confirmed.orderId(), confirmed.items().get(0).orderItemId());
        OrderDetail cancelled = place();
        eventDelivery.deliver(stockDeducted(cancelled.orderId()));
        List<DomainEvent> published = publishedOrderEvents();

        OutboxRelayResult result = relayService.relay();

        assertThat(published).extracting(DomainEvent::eventType).containsExactlyInAnyOrder("order.created",
            "order.payment-requested", "order.cancelled", "order.created", "order.payment-requested",
            "order-item.purchase-confirmed");
        assertThat(result).isEqualTo(new OutboxRelayResult(OutboxRelayResult.Outcome.COMPLETED, 6, 6));
        Map<String, ConsumerRecord<String, String>> received = read(TOPIC,
            published.stream().map(event -> event.eventId().toString()).collect(Collectors.toSet()));
        assertThat(received).hasSize(published.size());
        for (DomainEvent event : published) {
            ConsumerRecord<String, String> record = received.get(event.eventId().toString());
            assertThat(record.key()).isEqualTo(event.correlationId());
            assertThat(header(record, "eventType")).isEqualTo(event.eventType());
            assertThat(header(record, "eventId")).isEqualTo(event.eventId().toString());
            JsonNode body = jsonMapper.readTree(record.value());
            assertThat(body.propertyNames()).containsExactly("eventId", "eventType", "schemaVersion",
                "occurredAt", "producer", "aggregateId", "correlationId", "data");
            assertThat(body.get("eventType").asString()).isEqualTo(event.eventType());
            assertThat(body.get("producer").asString()).isEqualTo("order");
            assertThat(body.get("aggregateId").asString()).isEqualTo(event.aggregateId());
            assertThat(body.get("correlationId").asString()).isEqualTo(event.correlationId());
            // 노드 비교는 숫자 타입(int · long)까지 따져 같은 JSON도 다르다고 하므로 JSON 문자열로 비교한다
            assertThat(body.get("data").toString()).isEqualTo(jsonMapper.writeValueAsString(event.data()));
        }
        assertThat(partitionCount(TOPIC)).isEqualTo(3);
    }

    @Test
    @DisplayName("보내지 못한 메시지에서 멈췄다가, 받을 수 있게 되면 다음 relay()가 그 메시지부터 같은 순서로 보낸다")
    void resendsFromFailedMessageInOrder() throws Exception {
        String lateTopic = "late-" + UUID.randomUUID();
        List<OrderOutboxMessage> messages = List.of(save(lateTopic), save(lateTopic), save(lateTopic));

        OutboxRelayResult failed = relayService.relay();
        createTopic(lateTopic);
        OutboxRelayResult resent = relayService.relay();

        assertThat(failed).isEqualTo(new OutboxRelayResult(OutboxRelayResult.Outcome.STOPPED, 3, 0));
        assertThat(resent).isEqualTo(new OutboxRelayResult(OutboxRelayResult.Outcome.COMPLETED, 3, 3));
        List<String> eventIds = messages.stream().map(message -> message.getEventId().toString()).toList();
        Map<String, ConsumerRecord<String, String>> received = read(lateTopic, Set.copyOf(eventIds));
        assertThat(eventIds).extracting(eventId -> received.get(eventId).offset()).containsExactly(0L, 1L, 2L);
    }

    private List<DomainEvent> publishedOrderEvents() {
        return recorder.events(DomainEvent.class).stream()
            .filter(event -> event instanceof OrderCreatedEvent || event instanceof OrderPaymentRequestedEvent
                || event instanceof OrderCancelledEvent || event instanceof OrderItemPurchaseConfirmedEvent)
            .toList();
    }

    /**
     * 토픽의 모든 파티션을 처음부터 읽어 원하는 이벤트 ID가 모두 모일 때까지(최대 READ_TIMEOUT) 모은다.
     */
    private Map<String, ConsumerRecord<String, String>> read(String topic, Set<String> eventIds) {
        Map<String, ConsumerRecord<String, String>> found = new HashMap<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            List<TopicPartition> partitions = consumer.partitionsFor(topic).stream()
                .map(info -> new TopicPartition(topic, info.partition()))
                .toList();
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            long deadline = System.nanoTime() + READ_TIMEOUT.toNanos();
            while (found.size() < eventIds.size() && System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    String eventId = header(record, "eventId");
                    if (eventIds.contains(eventId)) {
                        found.put(eventId, record);
                    }
                }
            }
        }
        return found;
    }

    private int partitionCount(String topic) {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            return consumer.partitionsFor(topic).size();
        }
    }

    private void createTopic(String topic) throws Exception {
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
            kafka.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1))).all().get(10, TimeUnit.SECONDS);
        }
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private OrderOutboxMessage save(String topic) {
        return outboxRepository.save(OrderOutboxMessage.create(UUID.randomUUID(), "order.created", topic, "1024",
            "{}"));
    }

    private OrderDetail place() {
        return orderService.place(BUYER_ID, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, 1L, 2, 25_000L)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1")));
    }

    private static StockDeductedEvent stockDeducted(Long orderId) {
        return new StockDeductedEvent(UUID.randomUUID(), LocalDateTime.now(), new StockDeductedEvent.Data(orderId));
    }
}
