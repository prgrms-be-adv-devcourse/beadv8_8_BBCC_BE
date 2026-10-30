package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.productOption;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendKafkaMessages.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChild;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChildRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProductRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaSeller;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaSellerRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRange;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 추천이 판매자 · 자녀 · 상품 토픽의 메시지를 받아 복사본에 반영하고, 처리하지 못한 메시지를 DLT로 보내는지 확인한다(#576).
 * 메시지는 보내는 도메인을 거치지 않고 팀 Kafka 규칙의 형식으로 토픽에 직접 넣는다. 그래야 다시 받은 메시지 · 깨진 본문 ·
 * 받지 않는 종류처럼 보내는 도메인이 만들지 않는 경우를 정확히 넣을 수 있다. 보내는 도메인이 실제로 보낸 메시지를 받는지는
 * RecommendKafkaFromProducersIntegrationTest가 확인한다.
 * 같은 토픽에는 다른 테스트의 메시지도 쌓여 이어서 받을 수 있으므로, 판매자 · 자녀 · 상품 ID를 테스트마다 새로 만든다.
 */
@KafkaIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class RecommendKafkaListenerIntegrationTest {

    private static final String SELLER_TOPIC = "kidly.seller.events";
    private static final String CHILD_TOPIC = "kidly.childprofile.events";
    private static final String PRODUCT_TOPIC = "kidly.product.events";
    private static final String SELLER_DLT = "kidly.seller.events.recommend.dlt";
    private static final String CHILD_DLT = "kidly.childprofile.events.recommend.dlt";
    private static final String PRODUCT_DLT = "kidly.product.events.recommend.dlt";
    private static final LocalDateTime CHANGED_AT = LocalDateTime.of(2026, 10, 30, 10, 0);

    // 재시도는 1초 간격 3번이라, 재시도했다면 DLT에 들어가기까지 3초 이상 걸린다
    private static final long RETRY_BACKOFF_TOTAL_MS = 3_000;

    @Autowired
    private RecommendReplicaSellerRepository replicaSellerRepository;

    @Autowired
    private RecommendReplicaChildRepository replicaChildRepository;

    @Autowired
    private RecommendReplicaProductRepository replicaProductRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

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
    @DisplayName("판매자 정보 변경됨을 Kafka로 받아 판매자 복사본에 저장한다")
    void saveSellerReplica() throws Exception {
        Long sellerId = newId();

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

    @Test
    @DisplayName("자녀 정보 변경됨을 Kafka로 받아 자녀 복사본에 저장한다")
    void saveChildReplica() throws Exception {
        Long childId = newId();
        Long memberId = newId();

        messages.send(CHILD_TOPIC, new ChildChangedEvent(UUID.randomUUID(), CHANGED_AT,
            new ChildChangedEvent.Data(childId, memberId, new BigDecimal("115.0"), new BigDecimal("20.0"),
                Gender.MALE, LocalDate.of(2020, 5, 1), BodyType.NORMAL, PreferredFit.LOOSE, false)));

        AsyncWait.until(TIMEOUT, () -> replicaChildRepository.findById(childId).isPresent());
        RecommendReplicaChild replica = replicaChildRepository.findById(childId).orElseThrow();
        assertThat(replica.getHeightCm()).isEqualByComparingTo("115.0");
        assertThat(replica.getWeightKg()).isEqualByComparingTo("20.0");
        assertThat(replica)
            .extracting(
                RecommendReplicaChild::getMemberId,
                RecommendReplicaChild::getGender,
                RecommendReplicaChild::getBirthDate,
                RecommendReplicaChild::getBodyType,
                RecommendReplicaChild::getPreferredFit,
                RecommendReplicaChild::getEventOccurredAt,
                RecommendReplicaChild::isDeleted)
            .containsExactly(memberId, Gender.MALE, LocalDate.of(2020, 5, 1), BodyType.NORMAL, PreferredFit.LOOSE,
                CHANGED_AT, false);
    }

    @Test
    @DisplayName("상품 정보 변경됨을 Kafka로 받아 상품 복사본에 저장한다")
    void saveProductReplica() throws Exception {
        Long productId = newId();

        messages.send(PRODUCT_TOPIC, productChanged(productId));

        AsyncWait.until(TIMEOUT, () -> replicaProductRepository.findById(productId).isPresent());
        assertThat(replicaProductRepository.findById(productId)).get()
            .extracting(
                RecommendReplicaProduct::getSellerId,
                RecommendReplicaProduct::getCategoryId,
                RecommendReplicaProduct::getName,
                RecommendReplicaProduct::getStatus,
                RecommendReplicaProduct::getThumbnailUrl,
                RecommendReplicaProduct::getProductRegisteredAt,
                RecommendReplicaProduct::isSizeFree,
                RecommendReplicaProduct::getMinPrice,
                RecommendReplicaProduct::getEventOccurredAt)
            .containsExactly(7L, 3L, "데일리 맨투맨", ProductStatus.ON_SALE, "https://img.kidly.dev/a.jpg",
                CHANGED_AT.minusDays(1), false, 25_000L, CHANGED_AT);
        // 옵션 목록까지 본문에서 읽었는지 사이즈 줄로 확인한다. 사이즈 줄은 지연 로딩이라 트랜잭션 안에서 읽는다
        List<SizeRange> sizes = transactionTemplate.execute(status ->
            replicaProductRepository.findById(productId).orElseThrow().toProductSizes().sizeRanges());
        assertThat(sizes).containsExactlyInAnyOrder(SizeRange.of("110", 105, 115), SizeRange.of("120", 115, 125));
    }

    @Test
    @DisplayName("INV-R01 이미 반영한 메시지를 다시 받아도 그 뒤에 반영한 더 늦은 값이 그대로 남는다")
    void keepLaterValueWhenOldMessageRedelivered() throws Exception {
        Long sellerId = newId();
        var first = new SellerChangedEvent(UUID.randomUUID(), CHANGED_AT,
            new SellerChangedEvent.Data(sellerId, "소소한옷장", SellerStatus.ACTIVE));
        var later = new SellerChangedEvent(UUID.randomUUID(), CHANGED_AT.plusMinutes(1),
            new SellerChangedEvent.Data(sellerId, "새옷장", SellerStatus.SUSPENDED));

        messages.send(SELLER_TOPIC, first);
        messages.send(SELLER_TOPIC, later);
        var redelivered = messages.send(SELLER_TOPIC, first);

        messages.awaitProcessed(SELLER_TOPIC, redelivered);
        assertThat(replicaSellerRepository.findById(sellerId)).get()
            .extracting(
                RecommendReplicaSeller::getBusinessName,
                RecommendReplicaSeller::getStatus,
                RecommendReplicaSeller::getEventOccurredAt)
            .containsExactly("새옷장", SellerStatus.SUSPENDED, CHANGED_AT.plusMinutes(1));
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
    @DisplayName("더 늦게 일어난 이벤트에 필수 값이 없으면 재시도 없이 DLT로 보내고 복사본은 이전 값 그대로다")
    void deadLetterLaterChangeWithoutRequiredValue() throws Exception {
        Long childId = newId();
        messages.send(CHILD_TOPIC, childChanged(childId, new BigDecimal("115.0"), CHANGED_AT));
        AsyncWait.until(TIMEOUT, () -> replicaChildRepository.findById(childId).isPresent());

        var sent = messages.send(CHILD_TOPIC, childChanged(childId, null, CHANGED_AT.plusMinutes(1)));

        String key = String.valueOf(childId);
        AsyncWait.until(TIMEOUT, () -> !messages.deadLetters(CHILD_DLT, key).isEmpty());
        assertThat(messages.deadLetters(CHILD_DLT, key)).singleElement()
            .satisfies(deadLetter -> assertThat(deadLetter.timestamp() - sent.timestamp())
                .isLessThan(RETRY_BACKOFF_TOTAL_MS));
        RecommendReplicaChild replica = replicaChildRepository.findById(childId).orElseThrow();
        assertThat(replica.getHeightCm()).isEqualByComparingTo("115.0");
        assertThat(replica.getEventOccurredAt()).isEqualTo(CHANGED_AT);
    }

    @Test
    @DisplayName("상품 토픽에서 추천이 받지 않는 재고 차감됨은 건너뛰고 DLT로 보내지 않으며, 뒤 메시지를 처리한다")
    void skipStockDeducted() throws Exception {
        Long productId = newId();
        String key = String.valueOf(productId);
        messages.sendRaw(PRODUCT_TOPIC, key, "stock.deducted", UUID.randomUUID(), "받지 않는 종류라 읽지 않는 본문");

        // 키가 같아 같은 파티션이므로, 뒤 메시지를 처리했으면 앞 메시지는 이미 처리됐다
        messages.send(PRODUCT_TOPIC, productChanged(productId));

        AsyncWait.until(TIMEOUT, () -> replicaProductRepository.findById(productId).isPresent());
        assertThat(messages.deadLetters(PRODUCT_DLT, key)).isEmpty();
    }

    @Test
    @DisplayName("DLT로 보낼 때 로그에 키는 남기고 본문 값(자녀의 몸무게 · 생년월일)은 남기지 않는다")
    void logDeadLetterWithoutBody(CapturedOutput output) throws Exception {
        Long childId = newId();
        var event = new ChildChangedEvent(UUID.randomUUID(), CHANGED_AT, new ChildChangedEvent.Data(childId, 1L,
            null, new BigDecimal("37.7"), Gender.FEMALE, LocalDate.of(2019, 11, 23), BodyType.SLIM, PreferredFit.SLIM,
            false));

        messages.send(CHILD_TOPIC, event);

        String key = String.valueOf(childId);
        AsyncWait.until(TIMEOUT, () -> !messages.deadLetters(CHILD_DLT, key).isEmpty());
        // 숫자만으로는 로그 시각 등과 우연히 겹칠 수 있어, 본문에만 있는 칸 이름과 날짜로 확인한다
        assertThat(output.getOut())
            .contains("key=" + key)
            .doesNotContain("\"weightKg\"")
            .doesNotContain("2019-11-23");
    }

    private static Long newId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }

    // 상품 ID만 바꾼 상품 정보 변경됨이다. 판매자 7, 카테고리 3이고 110 · 120 두 옵션이 있다
    private static ProductChangedEvent productChanged(Long productId) {
        return new ProductChangedEvent(UUID.randomUUID(), CHANGED_AT, new ProductChangedEvent.Data(
            productId, 7L, "데일리 맨투맨", 3L, ProductStatus.ON_SALE, "https://img.kidly.dev/a.jpg",
            CHANGED_AT.minusDays(1), false,
            List.of(productOption(1L, "110", 105, 115), productOption(2L, "120", 115, 125))));
    }

    // 키만 바꾼 자녀 정보 변경됨이다. 회원 ID와 나머지 값은 고정이다
    private static ChildChangedEvent childChanged(Long childId, BigDecimal heightCm, LocalDateTime occurredAt) {
        return new ChildChangedEvent(UUID.randomUUID(), occurredAt, new ChildChangedEvent.Data(childId, 1L, heightCm,
            new BigDecimal("20.0"), Gender.MALE, LocalDate.of(2020, 5, 1), BodyType.NORMAL, PreferredFit.LOOSE, false));
    }
}
