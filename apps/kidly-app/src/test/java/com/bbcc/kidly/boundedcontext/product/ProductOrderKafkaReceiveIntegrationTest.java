package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.OrderCancelledFixture.cancelledItem;
import static com.bbcc.kidly.boundedcontext.product.OrderCancelledFixture.orderCancelled;
import static com.bbcc.kidly.boundedcontext.product.OrderCreatedFixture.orderCreated;
import static com.bbcc.kidly.boundedcontext.product.OrderCreatedFixture.orderItem;
import static com.bbcc.kidly.boundedcontext.product.ProductKafkaMessages.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.product.ProductOptionSaver.OnSaleOptions;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
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
 * 상품이 주문 토픽의 주문 생성됨 · 주문 취소됨을 받아 재고를 줄이고 되돌리는지 확인한다(#692).
 * 메시지는 주문 도메인을 거치지 않고 팀 Kafka 규칙의 형식으로 토픽에 직접 넣는다.
 * 테스트 설정은 Spring 경로도 켜 두므로, 주문 서비스를 거치면 Spring 이벤트로도 재고가 바뀌어 Kafka 수신을 확인할 수 없다.
 * DB의 ID는 테스트마다 1부터 다시 시작한다. 다른 테스트가 토픽에 남긴 주문 메시지가 이번 테스트의 옵션을 바꾸지 않도록,
 * 리스너를 켠 뒤 남은 메시지를 모두 처리하고 나서 옵션을 저장한다. 주문 ID는 테스트마다 새로 만든다.
 */
@KafkaIntegrationTest
class ProductOrderKafkaReceiveIntegrationTest {

    private static final String ORDER_TOPIC = "kidly.order.events";

    @Autowired
    private ProductOptionSaver productOptionSaver;

    @Autowired
    private InventoryDeductionDb inventoryDeductionDb;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Autowired
    private KafkaContainer kafka;

    private ProductKafkaMessages messages;

    private List<MessageListenerContainer> listeners;

    // 리스너는 이 테스트 안에서만 켠다
    @BeforeEach
    void startListeners() {
        messages = new ProductKafkaMessages(kafka.getBootstrapServers(), jsonMapper);
        listeners = ProductKafkaMessages.startListeners(kafkaListenerEndpointRegistry);
        messages.awaitCaughtUp(ORDER_TOPIC);
    }

    // stop()은 처리 중인 메시지가 끝날 때까지 기다리므로 뒤이은 불변식 확인 · 테이블 비우기와 겹치지 않는다
    @AfterEach
    void stopListenersAndCheckInvariants() {
        listeners.forEach(MessageListenerContainer::stop);
        inventoryDeductionDb.assertInvariants();
    }

    @Test
    @DisplayName("주문 생성됨을 Kafka로 받아 재고를 줄이고 차감 기록을 DEDUCTED로 남긴다")
    void deductOnOrderCreated() throws Exception {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);
        long orderId = newId();

        messages.send(ORDER_TOPIC, orderCreated(orderId, orderItem(11L, options.navy110(), 2)));

        AsyncWait.until(TIMEOUT, () -> !inventoryDeductionDb.records().isEmpty());
        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(8);
        assertThat(inventoryDeductionDb.records())
            .containsExactly(tuple(orderId, 11L, options.navy110().getId(), 2, "DEDUCTED"));
    }

    @Test
    @DisplayName("INV-07 주문 취소됨을 Kafka로 받아 그 주문에서 차감한 재고를 되돌리고 차감 기록을 RESTORED로 바꾼다")
    void restoreOnOrderCancelled() throws Exception {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);
        long orderId = newId();
        messages.send(ORDER_TOPIC, orderCreated(orderId, orderItem(11L, options.navy110(), 2)));

        messages.send(ORDER_TOPIC, orderCancelled(orderId, cancelledItem(11L, options.navy110(), 2)));

        AsyncWait.until(TIMEOUT, () -> inventoryDeductionDb.countByStatus("RESTORED") == 1);
        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(10);
        assertThat(inventoryDeductionDb.records())
            .containsExactly(tuple(orderId, 11L, options.navy110().getId(), 2, "RESTORED"));
    }

    private static long newId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }
}
