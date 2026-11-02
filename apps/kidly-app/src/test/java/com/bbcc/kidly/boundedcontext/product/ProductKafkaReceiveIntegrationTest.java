package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductKafkaMessages.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.domain.replica.ProductReplicaSeller;
import com.bbcc.kidly.boundedcontext.product.domain.replica.ProductReplicaSellerRepository;
import com.bbcc.kidly.boundedcontext.product.in.replica.ProductSellerKafkaListener;
import com.bbcc.kidly.boundedcontext.seller.app.port.SellerEventSender;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 상품이 판매자 토픽의 메시지를 받아 판매자 복사본에 반영하고, 처리하지 못한 메시지를 DLT로 보내는지 확인한다(#692).
 * 메시지는 판매자 도메인을 거치지 않고 팀 Kafka 규칙의 형식으로 토픽에 직접 넣는다. 그래야 다시 받은 메시지 · 깨진 본문 ·
 * 받지 않는 종류처럼 판매자가 만들지 않는 경우를 정확히 넣을 수 있다.
 * 판매자가 실제로 보낸 메시지를 받는지는 판매자의 Kafka 전송기로 보내 확인한다. 테스트 설정은 Spring 경로도 켜 두므로,
 * 판매자 서비스를 거치면 Spring 이벤트로도 복사본이 생겨 Kafka 수신을 확인할 수 없다.
 * 같은 토픽에는 다른 테스트의 메시지도 쌓여 이어서 받을 수 있으므로, 판매자 ID를 테스트마다 새로 만든다.
 */
@KafkaIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class ProductKafkaReceiveIntegrationTest {

    private static final String SELLER_TOPIC = "kidly.seller.events";
    private static final String SELLER_DLT = "kidly.seller.events.product.dlt";
    private static final LocalDateTime CHANGED_AT = LocalDateTime.of(2026, 11, 2, 10, 0);

    // 재시도는 1초 간격 3번이라, 재시도했다면 DLT에 들어가기까지 3초 이상 걸린다
    private static final long RETRY_BACKOFF_TOTAL_MS = 3_000;

    @Autowired
    private ProductReplicaSellerRepository replicaSellerRepository;

    @Autowired
    private SellerEventSender sellerEventSender;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private ProductKafkaListenerTestGuard listenerGuard;

    @Autowired
    private KafkaContainer kafka;

    private ProductKafkaMessages messages;

    private MessageListenerContainer listener;

    // 리스너는 ProductKafkaListenerTestGuard가 들고 있어 이 테스트 안에서만 켠다. 판매자 리스너만 켠다
    @BeforeEach
    void startListeners() {
        messages = new ProductKafkaMessages(kafka.getBootstrapServers(), jsonMapper);
        listener = ProductKafkaMessages.startListener(listenerGuard, ProductSellerKafkaListener.ID);
    }

    // stop()은 처리 중인 메시지가 끝날 때까지 기다리므로 뒤이은 테이블 비우기와 겹치지 않는다
    @AfterEach
    void stopListeners() {
        listener.stop();
    }

    @Test
    @DisplayName("판매자 정보 변경됨을 Kafka로 받아 판매자 복사본에 저장한다")
    void saveSellerReplica() throws Exception {
        Long sellerId = newId();

        messages.send(SELLER_TOPIC, sellerChanged(sellerId, "소소한옷장", CHANGED_AT));

        AsyncWait.until(TIMEOUT, () -> replicaSellerRepository.findById(sellerId).isPresent());
        assertThat(replicaSellerRepository.findById(sellerId)).get()
            .extracting(
                ProductReplicaSeller::getBusinessName,
                ProductReplicaSeller::getStatus,
                ProductReplicaSeller::getEventOccurredAt)
            .containsExactly("소소한옷장", SellerStatus.ACTIVE, CHANGED_AT);
    }

    @Test
    @DisplayName("판매자의 Kafka 전송기가 보낸 판매자 정보 변경됨을 받아 판매자 복사본에 저장한다")
    void saveSellerReplicaFromSeller() {
        Long sellerId = newId();

        sellerEventSender.send(sellerChanged(sellerId, "소소한옷장", CHANGED_AT));

        AsyncWait.until(TIMEOUT, () -> replicaSellerRepository.findById(sellerId).isPresent());
        assertThat(replicaSellerRepository.findById(sellerId)).get()
            .extracting(ProductReplicaSeller::getBusinessName, ProductReplicaSeller::getEventOccurredAt)
            .containsExactly("소소한옷장", CHANGED_AT);
    }

    @Test
    @DisplayName("이미 반영한 판매자 정보 변경됨을 다시 받아도 그 뒤에 반영한 더 늦은 값이 남고 DLT로 보내지 않는다")
    void keepLaterValueWhenOldMessageArrivesAgain() throws Exception {
        Long sellerId = newId();
        SellerChangedEvent first = sellerChanged(sellerId, "소소한옷장", CHANGED_AT);
        messages.send(SELLER_TOPIC, first);
        messages.send(SELLER_TOPIC, sellerChanged(sellerId, "소소한옷장 키즈", CHANGED_AT.plusMinutes(1)));
        AsyncWait.until(TIMEOUT, () -> replicaSellerRepository.findById(sellerId)
            .filter(seller -> "소소한옷장 키즈".equals(seller.getBusinessName())).isPresent());

        var resent = messages.send(SELLER_TOPIC, first);

        messages.awaitProcessed(SELLER_TOPIC, resent);
        assertThat(replicaSellerRepository.findById(sellerId)).get()
            .extracting(ProductReplicaSeller::getBusinessName, ProductReplicaSeller::getEventOccurredAt)
            .containsExactly("소소한옷장 키즈", CHANGED_AT.plusMinutes(1));
        assertThat(messages.deadLetters(SELLER_DLT, String.valueOf(sellerId))).isEmpty();
    }

    @Test
    @DisplayName("받는 종류인데 본문을 읽지 못하면 재시도 없이 DLT로 보낸다")
    void deadLetterUnreadableBody() throws Exception {
        String key = String.valueOf(newId());
        String body = "{\"data\":{\"sellerId\":\"숫자 아님\",\"businessName\":\"소소한옷장\"}}";

        var sent = messages.sendRaw(SELLER_TOPIC, key, "seller.changed", UUID.randomUUID(), body);

        AsyncWait.until(TIMEOUT, () -> !messages.deadLetters(SELLER_DLT, key).isEmpty());
        assertThat(messages.deadLetters(SELLER_DLT, key)).singleElement().satisfies(deadLetter -> {
            assertThat(deadLetter.value()).isEqualTo(body);
            assertThat(deadLetter.timestamp() - sent.timestamp()).isLessThan(RETRY_BACKOFF_TOTAL_MS);
        });
    }

    @Test
    @DisplayName("eventType 헤더가 없으면 어떤 이벤트인지 알 수 없어 재시도 없이 DLT로 보낸다")
    void deadLetterWithoutEventType() throws Exception {
        Long sellerId = newId();
        String key = String.valueOf(sellerId);
        String body = jsonMapper.writeValueAsString(sellerChanged(sellerId, "소소한옷장", CHANGED_AT));

        var sent = messages.sendRaw(SELLER_TOPIC, key, null, UUID.randomUUID(), body);

        AsyncWait.until(TIMEOUT, () -> !messages.deadLetters(SELLER_DLT, key).isEmpty());
        assertThat(messages.deadLetters(SELLER_DLT, key)).singleElement()
            .satisfies(deadLetter -> assertThat(deadLetter.timestamp() - sent.timestamp())
                .isLessThan(RETRY_BACKOFF_TOTAL_MS));
        assertThat(replicaSellerRepository.findById(sellerId)).isEmpty();
    }

    @Test
    @DisplayName("판매자 토픽에서 상품이 받지 않는 종류는 건너뛰고 DLT로 보내지 않으며, 뒤 메시지를 처리한다")
    void skipUnknownSellerEvent() throws Exception {
        Long sellerId = newId();
        String key = String.valueOf(sellerId);
        messages.sendRaw(SELLER_TOPIC, key, "seller.unknown", UUID.randomUUID(), "받지 않는 종류라 읽지 않는 본문");

        // 키가 같아 같은 파티션이므로, 뒤 메시지를 처리했으면 앞 메시지는 이미 처리됐다
        messages.send(SELLER_TOPIC, sellerChanged(sellerId, "소소한옷장", CHANGED_AT));

        AsyncWait.until(TIMEOUT, () -> replicaSellerRepository.findById(sellerId).isPresent());
        assertThat(messages.deadLetters(SELLER_DLT, key)).isEmpty();
    }

    @Test
    @DisplayName("DLT로 보낼 때 로그에 키는 남기고 본문 값은 남기지 않는다")
    void logDeadLetterWithoutBody(CapturedOutput output) throws Exception {
        String key = String.valueOf(newId());
        String body = "{\"data\":{\"sellerId\":\"숫자 아님\",\"businessName\":\"본문에만 있는 상호\"}}";

        messages.sendRaw(SELLER_TOPIC, key, "seller.changed", UUID.randomUUID(), body);

        AsyncWait.until(TIMEOUT, () -> !messages.deadLetters(SELLER_DLT, key).isEmpty());
        assertThat(output.getOut())
            .contains("key=" + key)
            .doesNotContain("본문에만 있는 상호")
            .doesNotContain("\"businessName\"");
    }

    private static Long newId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }

    private static SellerChangedEvent sellerChanged(Long sellerId, String businessName, LocalDateTime occurredAt) {
        return new SellerChangedEvent(UUID.randomUUID(), occurredAt,
            new SellerChangedEvent.Data(sellerId, businessName, SellerStatus.ACTIVE));
    }
}
