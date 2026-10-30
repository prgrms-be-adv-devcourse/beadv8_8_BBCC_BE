package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendKafkaMessages.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.purchase.PurchaseRecord;
import com.bbcc.kidly.boundedcontext.recommend.out.purchase.JpaPurchaseRecordRepository;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
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
 * 추천이 주문 토픽의 구매확정을 받아 구매 기록에 반영하는지 확인한다(#606).
 * 메시지는 팀 Kafka 규칙의 형식으로 토픽에 직접 넣는다. 주문이 실제로 보낸 메시지를 받는지는
 * RecommendKafkaFromProducersIntegrationTest가 확인한다.
 * 같은 토픽에는 다른 테스트의 주문 메시지도 쌓여 이어서 받을 수 있으므로, 주문 · 주문상품 ID를 테스트마다 새로 만든다.
 */
@KafkaIntegrationTest
class RecommendPurchaseKafkaIntegrationTest {

    private static final String ORDER_TOPIC = "kidly.order.events";
    private static final LocalDateTime CONFIRMED_AT = LocalDateTime.of(2026, 10, 30, 12, 0);

    @Autowired
    private JpaPurchaseRecordRepository purchaseRecordRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Autowired
    private KafkaContainer kafka;

    private RecommendKafkaMessages messages;

    private List<MessageListenerContainer> listeners;

    // 리스너는 이 테스트 안에서만 켠다
    @BeforeEach
    void startListeners() {
        messages = new RecommendKafkaMessages(kafka.getBootstrapServers(), jsonMapper);
        listeners = RecommendKafkaMessages.startListeners(kafkaListenerEndpointRegistry);
    }

    // stop()은 처리 중인 메시지가 끝날 때까지 기다리므로 뒤이은 테이블 비우기와 겹치지 않는다
    @AfterEach
    void stopListeners() {
        listeners.forEach(MessageListenerContainer::stop);
    }

    @Test
    @DisplayName("주문상품 구매확정을 Kafka로 받아 구매 기록에 저장한다")
    void savePurchaseRecord() throws Exception {
        Long orderItemId = newId();
        Long orderId = newId();

        messages.send(ORDER_TOPIC, purchaseConfirmed(orderItemId, orderId));

        AsyncWait.until(TIMEOUT, () -> !recordsOf(orderItemId).isEmpty());
        assertThat(recordsOf(orderItemId)).singleElement()
            .extracting(
                PurchaseRecord::getOrderId,
                PurchaseRecord::getMemberId,
                PurchaseRecord::getChildProfileId,
                PurchaseRecord::getProductId,
                PurchaseRecord::getProductOptionId,
                PurchaseRecord::getSize,
                PurchaseRecord::getConfirmedAt)
            .containsExactly(orderId, 1L, 11L, 21L, 31L, "120", CONFIRMED_AT);
    }

    private List<PurchaseRecord> recordsOf(Long orderItemId) {
        return purchaseRecordRepository.findAll().stream()
            .filter(record -> orderItemId.equals(record.getOrderItemId()))
            .toList();
    }

    private static Long newId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }

    // 주문상품 · 주문 ID만 바꾼 구매확정이다. 회원 1이 자녀 11을 위해 상품 21의 옵션 31(사이즈 120)을 샀다
    private static OrderItemPurchaseConfirmedEvent purchaseConfirmed(Long orderItemId, Long orderId) {
        return new OrderItemPurchaseConfirmedEvent(UUID.randomUUID(), CONFIRMED_AT.plusSeconds(1),
            new OrderItemPurchaseConfirmedEvent.Data(orderItemId, orderId, 1L, 7L, 11L, 21L, 31L, "120", 2, 50_000L,
                CONFIRMED_AT));
    }
}
