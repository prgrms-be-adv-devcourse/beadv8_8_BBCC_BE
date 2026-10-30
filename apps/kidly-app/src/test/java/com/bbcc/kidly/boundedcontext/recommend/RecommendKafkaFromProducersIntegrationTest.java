package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.productOption;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendKafkaMessages.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.childprofile.app.outbox.ChildProfileOutboxRelayService;
import com.bbcc.kidly.boundedcontext.product.app.outbox.ProductOutboxRelayService;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChild;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChildRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProductRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaSeller;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaSellerRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 판매자 · 자녀 · 상품 도메인이 실제로 보낸 메시지를 추천이 읽어 복사본에 저장하는지 확인한다(#576).
 * 보내는 쪽이 만드는 본문(공통 칸 7개 + data)과 추천이 읽는 형식이 맞는지 보는 도메인 사이 계약 테스트다.
 * 판매자는 커밋 뒤 바로 보내고, 자녀 · 상품은 아웃박스에 저장된 뒤 릴레이가 보낸다.
 * 테스트 설정은 릴레이 주기가 길어 저절로 돌지 않으므로 테스트가 릴레이를 직접 부른다.
 */
@KafkaIntegrationTest
class RecommendKafkaFromProducersIntegrationTest {

    private static final LocalDateTime CHANGED_AT = LocalDateTime.of(2026, 10, 30, 11, 0);

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ChildProfileOutboxRelayService childProfileOutboxRelayService;

    @Autowired
    private ProductOutboxRelayService productOutboxRelayService;

    @Autowired
    private RecommendReplicaSellerRepository replicaSellerRepository;

    @Autowired
    private RecommendReplicaChildRepository replicaChildRepository;

    @Autowired
    private RecommendReplicaProductRepository replicaProductRepository;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    private List<MessageListenerContainer> listeners;

    // 리스너는 이 테스트 안에서만 켠다
    @BeforeEach
    void startListeners() {
        listeners = RecommendKafkaMessages.startListeners(kafkaListenerEndpointRegistry);
    }

    // stop()은 처리 중인 메시지가 끝날 때까지 기다리므로 뒤이은 테이블 비우기와 겹치지 않는다
    @AfterEach
    void stopListeners() {
        listeners.forEach(MessageListenerContainer::stop);
    }

    @Test
    @DisplayName("판매자 · 자녀 · 상품 도메인이 커밋 뒤 보낸 이벤트를 Kafka로 받아 추천 복사본에 저장한다")
    void saveReplicasFromProducers() {
        Long sellerId = newId();
        Long childId = newId();
        Long productId = newId();

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(new SellerChangedEvent(UUID.randomUUID(), CHANGED_AT,
                new SellerChangedEvent.Data(sellerId, "소소한옷장", SellerStatus.ACTIVE)));
            eventPublisher.publish(new ChildChangedEvent(UUID.randomUUID(), CHANGED_AT,
                new ChildChangedEvent.Data(childId, 1L, new BigDecimal("115.0"), new BigDecimal("20.0"),
                    Gender.MALE, LocalDate.of(2020, 5, 1), BodyType.NORMAL, PreferredFit.LOOSE, false)));
            eventPublisher.publish(new ProductChangedEvent(UUID.randomUUID(), CHANGED_AT,
                new ProductChangedEvent.Data(productId, sellerId, "데일리 맨투맨", 3L, ProductStatus.ON_SALE,
                    "https://img.kidly.dev/a.jpg", CHANGED_AT.minusDays(1), false,
                    List.of(productOption(1L, "110", 105, 115), productOption(2L, "120", 115, 125)))));
        });
        childProfileOutboxRelayService.relay();
        productOutboxRelayService.relay();

        AsyncWait.until(TIMEOUT, () -> replicaSellerRepository.findById(sellerId).isPresent()
            && replicaChildRepository.findById(childId).isPresent()
            && replicaProductRepository.findById(productId).isPresent());
        assertThat(replicaSellerRepository.findById(sellerId)).get()
            .extracting(RecommendReplicaSeller::getBusinessName, RecommendReplicaSeller::getEventOccurredAt)
            .containsExactly("소소한옷장", CHANGED_AT);
        assertThat(replicaChildRepository.findById(childId)).get()
            .extracting(RecommendReplicaChild::getMemberId, RecommendReplicaChild::getEventOccurredAt)
            .containsExactly(1L, CHANGED_AT);
        assertThat(replicaProductRepository.findById(productId)).get()
            .extracting(RecommendReplicaProduct::getName, RecommendReplicaProduct::getMinPrice,
                RecommendReplicaProduct::getEventOccurredAt)
            .containsExactly("데일리 맨투맨", 25_000L, CHANGED_AT);
    }

    private static Long newId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }
}
