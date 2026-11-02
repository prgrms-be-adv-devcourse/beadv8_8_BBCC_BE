package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendKafkaMessages.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaSeller;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaSellerRepository;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
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
 * 추천 서비스가 kidly-app과 같은 구독 그룹(kidly.recommend)으로 Kafka에 붙어 받은 메시지를 복사본에 반영하는지 본다.
 * 받는 종류마다의 처리는 kidly-app의 추천 수신 테스트가 확인하고, 여기서는 서비스가 Kafka에 붙는지만 판매자 메시지 하나로 본다.
 */
@KafkaIntegrationTest
class RecommendServiceKafkaIntegrationTest {

    private static final String SELLER_TOPIC = "kidly.seller.events";
    private static final LocalDateTime CHANGED_AT = LocalDateTime.of(2026, 11, 2, 10, 0);

    @Autowired
    private RecommendReplicaSellerRepository replicaSellerRepository;

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
    @DisplayName("판매자 정보 변경됨을 추천 그룹으로 받아 판매자 복사본에 저장한다")
    void saveSellerReplica() throws Exception {
        // 같은 토픽에 다른 테스트의 메시지가 쌓여 있을 수 있으므로 판매자 ID를 새로 만든다
        Long sellerId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);

        messages.send(SELLER_TOPIC, new SellerChangedEvent(UUID.randomUUID(), CHANGED_AT,
            new SellerChangedEvent.Data(sellerId, "소소한옷장", SellerStatus.ACTIVE)));

        AsyncWait.until(TIMEOUT, () -> replicaSellerRepository.findById(sellerId).isPresent());
        assertThat(replicaSellerRepository.findById(sellerId)).get()
            .extracting(
                RecommendReplicaSeller::getBusinessName,
                RecommendReplicaSeller::getStatus,
                RecommendReplicaSeller::getEventOccurredAt)
            .containsExactly("소소한옷장", SellerStatus.ACTIVE, CHANGED_AT);
    }
}
