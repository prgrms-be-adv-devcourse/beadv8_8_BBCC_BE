package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.OrderCancelledFixture.cancelledItem;
import static com.bbcc.kidly.boundedcontext.product.OrderCancelledFixture.orderCancelled;
import static com.bbcc.kidly.boundedcontext.product.OrderCreatedFixture.orderCreated;
import static com.bbcc.kidly.boundedcontext.product.OrderCreatedFixture.orderItem;
import static com.bbcc.kidly.boundedcontext.product.OrderItemCancelledFixture.cancelledOrderItem;
import static com.bbcc.kidly.boundedcontext.product.OrderItemCancelledFixture.orderItemCancelled;
import static com.bbcc.kidly.boundedcontext.product.ProductKafkaMessages.TIMEOUT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.product.ProductOptionSaver.OnSaleOptions;
import com.bbcc.kidly.boundedcontext.product.in.inventory.ProductOrderKafkaListener;
import com.bbcc.kidly.shared.order.event.OrderCreatedEvent;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.KafkaHeaders;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 상품이 주문 토픽의 주문 생성됨 · 주문 취소됨 · 판매자 부분 취소(주문상품 취소됨)를 받아 재고를 줄이고 되돌리는지 확인한다(#692, #693).
 * 메시지는 주문 도메인을 거치지 않고 팀 Kafka 규칙의 형식으로 토픽에 직접 넣는다.
 * DB의 ID는 테스트마다 1부터 다시 시작한다. 다른 테스트가 토픽에 남긴 주문 메시지가 이번 테스트의 옵션을 바꾸지 않도록,
 * 리스너를 켠 뒤 남은 메시지를 모두 처리하고 나서 옵션을 저장한다. 주문 ID는 테스트마다 새로 만든다.
 */
@KafkaIntegrationTest
class ProductOrderKafkaReceiveIntegrationTest {

    private static final String ORDER_TOPIC = "kidly.order.events";
    private static final String ORDER_DLT = "kidly.order.events.product.dlt";

    // 재시도는 1초 간격 3번이라, 재시도했다면 DLT에 들어가기까지 3초 이상 걸린다
    private static final long RETRY_BACKOFF_TOTAL_MS = 3_000;

    @Autowired
    private ProductOptionSaver productOptionSaver;

    @Autowired
    private InventoryDeductionDb inventoryDeductionDb;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private ProductKafkaListenerTestGuard listenerGuard;

    @Autowired
    private KafkaContainer kafka;

    private ProductKafkaMessages messages;

    private MessageListenerContainer listener;

    // 리스너는 ProductKafkaListenerTestGuard가 들고 있어 이 테스트 안에서만 켠다. 주문 리스너만 켠다
    @BeforeEach
    void startListeners() {
        messages = new ProductKafkaMessages(kafka.getBootstrapServers(), jsonMapper);
        listener = ProductKafkaMessages.startListener(listenerGuard, ProductOrderKafkaListener.ID);
        messages.awaitCaughtUp(ORDER_TOPIC);
    }

    // stop()은 처리 중인 메시지가 끝날 때까지 기다리므로 뒤이은 불변식 확인 · 테이블 비우기와 겹치지 않는다
    @AfterEach
    void stopListenersAndCheckInvariants() {
        listener.stop();
        inventoryDeductionDb.assertInvariants();
    }

    @Test
    @DisplayName("INV-04 주문 생성됨을 Kafka로 받아 재고를 줄이고 차감 기록을 DEDUCTED로 남긴다")
    void deductOnOrderCreated() throws Exception {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);
        long orderId = newId();

        messages.send(ORDER_TOPIC, orderCreated(orderId, orderItem(11L, options.navy110(), 2)));

        AsyncWait.until(TIMEOUT, () -> !inventoryDeductionDb.records(orderId).isEmpty());
        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(8);
        assertThat(inventoryDeductionDb.records(orderId))
            .containsExactly(tuple(orderId, 11L, options.navy110().getId(), 2, "DEDUCTED"));
    }

    @Test
    @DisplayName("INV-07 주문 취소됨을 Kafka로 받아 그 주문에서 차감한 재고를 되돌리고 차감 기록을 RESTORED로 바꾼다")
    void restoreOnOrderCancelled() throws Exception {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);
        long orderId = newId();
        messages.send(ORDER_TOPIC, orderCreated(orderId, orderItem(11L, options.navy110(), 2)));

        messages.send(ORDER_TOPIC, orderCancelled(orderId, cancelledItem(11L, options.navy110(), 2)));

        var restored = tuple(orderId, 11L, options.navy110().getId(), 2, "RESTORED");
        AsyncWait.until(TIMEOUT, () -> List.of(restored).equals(inventoryDeductionDb.records(orderId)));
        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(10);
    }

    @Test
    @DisplayName("INV-10 같은 주문 생성됨을 Kafka로 두 번 받아도 재고는 한 번만 줄고 차감 기록은 한 줄이다")
    void deductOnceForRepeatedOrderCreated() throws Exception {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);
        long orderId = newId();
        OrderCreatedEvent event = orderCreated(orderId, orderItem(11L, options.navy110(), 2));
        messages.send(ORDER_TOPIC, event);

        var resent = messages.send(ORDER_TOPIC, event);

        messages.awaitProcessed(ORDER_TOPIC, resent);
        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(8);
        assertThat(inventoryDeductionDb.records(orderId))
            .containsExactly(tuple(orderId, 11L, options.navy110().getId(), 2, "DEDUCTED"));
    }

    @Test
    @DisplayName("INV-09 같은 주문의 주문 취소됨이 주문 생성됨보다 먼저 오면 FAILED를 남기고, 뒤에 온 주문 생성됨은 차감하지 않는다")
    void skipOrderCreatedAfterCancelled() throws Exception {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);
        long orderId = newId();
        messages.send(ORDER_TOPIC, orderCancelled(orderId, cancelledItem(11L, options.navy110(), 2)));

        // 키가 주문 ID로 같아 같은 파티션이므로, 뒤 메시지를 처리했으면 앞 메시지는 이미 처리됐다
        var created = messages.send(ORDER_TOPIC, orderCreated(orderId, orderItem(11L, options.navy110(), 2)));

        messages.awaitProcessed(ORDER_TOPIC, created);
        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(10);
        assertThat(inventoryDeductionDb.records(orderId))
            .containsExactly(tuple(orderId, 11L, options.navy110().getId(), 2, "FAILED"));
    }

    @Test
    @DisplayName("INV-04 판매자 부분 취소(주문상품 취소됨)를 Kafka로 받아 취소한 주문상품의 차감만 되돌리고, 같은 주문의 다른 주문상품은 DEDUCTED 그대로다")
    void restoreOnOrderItemCancelled() throws Exception {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);
        long orderId = newId();
        messages.send(ORDER_TOPIC, orderCreated(orderId,
            orderItem(11L, options.navy110(), 2),
            orderItem(12L, options.navy120(), 1)));

        messages.send(ORDER_TOPIC, orderItemCancelled(orderId, 7L, cancelledOrderItem(11L, options.navy110(), 2)));

        var restored = tuple(orderId, 11L, options.navy110().getId(), 2, "RESTORED");
        AsyncWait.until(TIMEOUT, () -> inventoryDeductionDb.records(orderId).contains(restored));
        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(10);
        assertThat(inventoryDeductionDb.availableQty(options.navy120())).isEqualTo(4);
        assertThat(inventoryDeductionDb.records(orderId)).containsExactlyInAnyOrder(
            restored, tuple(orderId, 12L, options.navy120().getId(), 1, "DEDUCTED"));
    }

    @Test
    @DisplayName("주문 토픽에서 상품이 받지 않는 종류(구매확정 등)는 건너뛰고 DLT로 보내지 않으며, 뒤 메시지를 처리한다")
    void skipUnhandledOrderEvent() throws Exception {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);
        long orderId = newId();
        String key = String.valueOf(orderId);
        messages.sendRaw(ORDER_TOPIC, key, "order-item.purchase-confirmed", UUID.randomUUID(), "받지 않는 종류라 읽지 않는 본문");

        // 키가 같아 같은 파티션이므로, 뒤 메시지를 처리했으면 앞 메시지는 이미 처리됐다
        messages.send(ORDER_TOPIC, orderCreated(orderId, orderItem(11L, options.navy110(), 2)));

        AsyncWait.until(TIMEOUT, () -> !inventoryDeductionDb.records(orderId).isEmpty());
        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(8);
        assertThat(messages.deadLetters(ORDER_DLT, key)).isEmpty();
    }

    @Test
    @DisplayName("주문 생성됨의 본문을 읽지 못하면 재시도 없이 상품의 주문 DLT로 보낸다")
    void deadLetterUnreadableOrderCreated() throws Exception {
        String key = String.valueOf(newId());
        String body = "{\"data\":{\"orderId\":\"숫자 아님\",\"items\":[]}}";

        var sent = messages.sendRaw(ORDER_TOPIC, key, "order.created", UUID.randomUUID(), body);

        AsyncWait.until(TIMEOUT, () -> !messages.deadLetters(ORDER_DLT, key).isEmpty());
        assertThat(messages.deadLetters(ORDER_DLT, key)).singleElement().satisfies(deadLetter -> {
            assertThat(deadLetter.value()).isEqualTo(body);
            assertThat(deadLetter.timestamp() - sent.timestamp()).isLessThan(RETRY_BACKOFF_TOTAL_MS);
        });
    }

    @Test
    @DisplayName("주문 토픽의 메시지에 eventType 헤더가 없으면 어떤 이벤트인지 알 수 없어 재시도 없이 IllegalArgumentException으로 DLT에 보낸다")
    void deadLetterWithoutEventType() throws Exception {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);
        long orderId = newId();
        String key = String.valueOf(orderId);
        String body = jsonMapper.writeValueAsString(orderCreated(orderId, orderItem(11L, options.navy110(), 2)));

        var sent = messages.sendRaw(ORDER_TOPIC, key, null, UUID.randomUUID(), body);

        AsyncWait.until(TIMEOUT, () -> !messages.deadLetters(ORDER_DLT, key).isEmpty());
        assertThat(messages.deadLetters(ORDER_DLT, key)).singleElement().satisfies(deadLetter -> {
            assertThat(deadLetter.timestamp() - sent.timestamp()).isLessThan(RETRY_BACKOFF_TOTAL_MS);
            var cause = deadLetter.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN);
            assertThat(new String(cause.value(), UTF_8)).isEqualTo(IllegalArgumentException.class.getName());
        });
        assertThat(inventoryDeductionDb.records(orderId)).isEmpty();
        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(10);
    }

    private static long newId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }
}
