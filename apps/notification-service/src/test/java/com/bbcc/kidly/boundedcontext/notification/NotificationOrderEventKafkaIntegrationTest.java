package com.bbcc.kidly.boundedcontext.notification;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotification;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationType;
import com.bbcc.kidly.boundedcontext.notification.out.JpaInboxNotificationRepository;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.order.event.DeliveryShippedEvent;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import com.bbcc.kidly.shared.order.event.WishSentEvent;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 결제가 kidly.payment.events에 보낸 지급 완료 · 환불 완료를 받아 메일을 보낸다(#879).
 * 이 서비스에는 결제 도메인이 없으므로 결제가 보내는 형식(키 correlationId, 헤더 eventType · eventId, 본문 공통 칸 + data)으로
 * 토픽에 직접 넣는다. 받는 사람 · 정산서 · 주문 ID를 테스트마다 새로 만들고, 결과는 이 테스트의 받는 사람으로 걸러 본다.
 */
/**
 * 주문이 kidly.order.events에 보낸 조르기 요청을 받아 보호자 알림함에 넣는다(#881).
 * 주문이 보내는 형식(키 correlationId, 헤더 eventType · eventId, 본문 공통 칸 + data)으로 토픽에 직접 넣는다.
 * 보호자 회원 ID를 테스트마다 새로 만들고, 결과는 그 회원의 알림함으로 걸러 본다.
 */
@KafkaIntegrationTest
class NotificationOrderEventKafkaIntegrationTest {

    private static final String LISTENER_ID = "notificationOrderEventListener";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JpaInboxNotificationRepository jpaInboxNotificationRepository;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Autowired
    private KafkaContainer kafka;

    private final Long memberId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    private final Long wishRequestId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);

    private MessageListenerContainer listener;

    @BeforeEach
    void startListener() {
        listener = kafkaListenerEndpointRegistry.getListenerContainer(LISTENER_ID);
        listener.start();
        AsyncWait.until(TIMEOUT, () -> !listener.getAssignedPartitions().isEmpty());
    }

    @AfterEach
    void stopListener() {
        listener.stop();
    }

    @Test
    @DisplayName("이 토픽은 책갈피가 없으므로 처음부터 읽어, 구독 전에 보낸 조르기를 놓치지 않는다")
    void readFromEarliest() {
        assertThat(listener.getContainerProperties().getKafkaConsumerProperties()
            .getProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG)).isEqualTo("earliest");
    }

    @Test
    @DisplayName("조르기 요청을 받으면 보호자 알림함에 넣고, 같은 메시지를 두 번 받아도 한 건이다")
    void addWishSent() throws Exception {
        var event = new WishSentEvent(UUID.randomUUID(), LocalDateTime.now(), new WishSentEvent.Data(
            wishRequestId, 3L, memberId, List.of(new WishSentEvent.Item(null, 5L, 1))));

        send(event);
        send(event);

        AsyncWait.until(TIMEOUT, () -> !inbox().isEmpty());
        assertThat(inbox()).singleElement().satisfies(notification -> {
            assertThat(notification.getType()).isEqualTo(NotificationType.WISH_SENT);
            assertThat(notification.getTitle()).isEqualTo("아이가 구매 요청을 보냈어요");
            assertThat(notification.getLinkPath()).isEqualTo("/my/children/3/wishes/" + wishRequestId);
        });
    }

    @Test
    @DisplayName("재고 부족으로 주문이 취소되면 구매자 알림함에 넣고, 구매자가 직접 취소한 주문은 넣지 않는다")
    void addOrderCancelled() throws Exception {
        var orderId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        send(new OrderCancelledEvent(UUID.randomUUID(), LocalDateTime.now(),
            new OrderCancelledEvent.Data(orderId, memberId, "BUYER_CANCELLED", List.of())));
        send(new OrderCancelledEvent(UUID.randomUUID(), LocalDateTime.now(),
            new OrderCancelledEvent.Data(orderId, memberId, "OUT_OF_STOCK", List.of())));

        // 키(주문 ID)가 같아 같은 파티션이다. 뒤 메시지가 들어왔으면 앞 메시지는 이미 처리됐다
        AsyncWait.until(TIMEOUT, () -> !inbox().isEmpty());
        assertThat(inbox()).singleElement().satisfies(notification -> {
            assertThat(notification.getType()).isEqualTo(NotificationType.ORDER_CANCELLED);
            assertThat(notification.getTitle()).isEqualTo("재고가 없어 주문이 취소됐어요");
            assertThat(notification.getLinkPath()).isEqualTo("/orders/" + orderId);
        });
    }

    @Test
    @DisplayName("배송이 출발하면 구매자 알림함에 넣고, 같은 메시지를 두 번 받아도 한 건이다")
    void addDeliveryShipped() throws Exception {
        var orderId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        var event = new DeliveryShippedEvent(UUID.randomUUID(), LocalDateTime.now(), new DeliveryShippedEvent.Data(
            ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE), orderId, memberId, 9L, "CJ", "CJ대한통운",
            "123456789012", LocalDateTime.now()));

        send(event);
        send(event);

        AsyncWait.until(TIMEOUT, () -> !inbox().isEmpty());
        assertThat(inbox()).singleElement().satisfies(notification -> {
            assertThat(notification.getType()).isEqualTo(NotificationType.DELIVERY_SHIPPED);
            assertThat(notification.getLinkPath()).isEqualTo("/orders/" + orderId);
        });
    }

    private List<InboxNotification> inbox() {
        return jpaInboxNotificationRepository.findAll().stream()
            .filter(notification -> memberId.equals(notification.getMemberId()))
            .toList();
    }

    // 주문이 보내는 형식 그대로 넣는다
    private void send(DomainEvent event) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", event.eventId());
        body.put("eventType", event.eventType());
        body.put("schemaVersion", event.schemaVersion());
        body.put("occurredAt", event.occurredAt());
        body.put("producer", event.producer());
        body.put("aggregateId", event.aggregateId());
        body.put("correlationId", event.correlationId());
        body.put("data", event.data());
        var record = new ProducerRecord<>("kidly.order.events", event.correlationId(),
            jsonMapper.writeValueAsString(body));
        record.headers().add("eventId", event.eventId().toString().getBytes(UTF_8));
        record.headers().add("eventType", event.eventType().getBytes(UTF_8));
        try (var producer = new KafkaProducer<String, String>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            producer.send(record).get(10, TimeUnit.SECONDS);
        }
    }
}
