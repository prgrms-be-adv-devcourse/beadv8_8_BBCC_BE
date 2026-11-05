package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_NAME;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_CHANGED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.productOption;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaProductService;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProductRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaSizeSpec;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.ProductSizes;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRange;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 상품 복사본 서비스가 실제 DB에서 상품 정보 변경됨을 반영하는 규칙을 확인한다.
 * Kafka로 받는 연결은 RecommendKafkaListenerIntegrationTest가 확인한다.
 */
@IntegrationTest
class RecommendReplicaProductIntegrationTest {

    private static final LocalDateTime LATER = PRODUCT_CHANGED_AT.plusMinutes(1);

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private RecommendReplicaProductRepository replicaProductRepository;

    @Autowired
    private RecommendReplicaProductService replicaProductService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 가장 낮은 판매가를 판매 가능한 옵션에 두어, 품절 · 판매 중지 옵션을 최저가에 넣을지와 상관없이 기대값이 하나로 정해진다
    @Test
    @DisplayName("상품 정보 변경됨을 받으면 상품명 · 최저가 · 판매 가능 사이즈가 상품 복사본에 저장된다")
    void saveNamePriceAndSellableSizes() {
        replicaProductService.applyProductChanged(RecommendFixture.productAChangedEventAt(List.of(
            productOption(1L, "120", 115, 125, 25_000L, true, OptionSaleStatus.ON_SALE),
            productOption(2L, "110", 105, 115, 23_000L, false, OptionSaleStatus.ON_SALE),
            productOption(3L, "130", 125, 135, 27_000L, false, OptionSaleStatus.STOPPED)), PRODUCT_CHANGED_AT));

        // 판매 가능 사이즈는 지연 로딩이라, 새 트랜잭션에서 읽어야 저장된 행을 실제로 불러온다
        transactionTemplate.executeWithoutResult(status -> {
            RecommendReplicaProduct replica = replicaProductRepository.findById(PRODUCT_A_ID).orElseThrow();
            assertThat(replica.getName()).isEqualTo(PRODUCT_A_NAME);
            assertThat(replica.getMinPrice()).isEqualTo(23_000L);
            assertThat(replica.isSellable("110")).isTrue();
            assertThat(replica.isSellable("120")).isFalse();
            assertThat(replica.isSellable("130")).isFalse();
            assertThat(replica.hasSellableOption()).isTrue();
        });
    }

    @Test
    @DisplayName("사이즈 무관 상품의 상품 정보 변경됨을 받으면 사이즈 무관으로 저장되고 권장 키 없는 옵션은 사이즈 줄에 넣지 않는다")
    void saveSizeFreeReplica() {
        replicaProductService.applyProductChanged(RecommendFixture.productAChangedEventOf(
            true, List.of(productOption(3L, "FREE", null, null)), PRODUCT_CHANGED_AT));

        ProductSizes sizes = sizesOf(PRODUCT_A_ID);
        assertThat(sizes.isSizeFree()).isTrue();
        assertThat(sizes.sizeRanges()).isEmpty();
    }

    // 사이즈 역할 항목이 없는 유형의 상품은 옵션 사이즈가 null로 오고, 이벤트의 사이즈 무관 값은 false로 온다
    @Test
    @DisplayName("사이즈 없는 상품의 상품 정보 변경됨을 받으면 사이즈 무관으로 저장되고 판매 가능한 옵션이 있는 상품이 된다")
    void saveReplicaOfProductWithoutSize() {
        replicaProductService.applyProductChanged(RecommendFixture.productAChangedEventOf(
            false, List.of(productOption(3L, null, null, null)), PRODUCT_CHANGED_AT));

        assertThat(sizesOf(PRODUCT_A_ID).isSizeFree()).isTrue();
        Boolean hasSellableOption = transactionTemplate.execute(
            status -> replicaProductRepository.findById(PRODUCT_A_ID).orElseThrow().hasSellableOption());
        assertThat(hasSellableOption).isTrue();
    }

    @Test
    @DisplayName("INV-R01 이미 있는 상품 복사본은 더 늦게 일어난 이벤트의 값으로 덮어써지고 사이즈 줄도 새 옵션으로 바뀐다")
    void overwriteWithLaterEvent() {
        replicaProductService.applyProductChanged(RecommendFixture.productAChangedEvent());

        replicaProductService.applyProductChanged(RecommendFixture.productAChangedEventAt(
            List.of(productOption(3L, "130", 125, 135)), LATER));

        assertThat(replicaProductRepository.findById(PRODUCT_A_ID)).get()
            .extracting(RecommendReplicaProduct::getEventOccurredAt).isEqualTo(LATER);
        assertThat(sizesOf(PRODUCT_A_ID).sizeRanges()).containsExactly(SizeRange.of("130", 125, 135));
    }

    @Test
    @DisplayName("INV-R01 더 늦게 일어난 이벤트를 받으면 판매 가능 사이즈 행이 새 옵션의 사이즈로 바뀌고 옛 사이즈 행은 남지 않는다")
    void replaceSellableSizeRowsWithLaterEvent() {
        replicaProductService.applyProductChanged(RecommendFixture.productAChangedEvent());

        replicaProductService.applyProductChanged(RecommendFixture.productAChangedEventAt(
            List.of(productOption(3L, "130", 125, 135)), LATER));

        assertThat(jdbcTemplate.queryForList(
            "select size from recommend.replica_product_sellable_size where product_id = ?", String.class,
            PRODUCT_A_ID)).containsExactly("130");
    }

    @ParameterizedTest(name = "저장된 시각에서 {0}분")
    @ValueSource(longs = {0, -1})
    @DisplayName("INV-R01 같은 시각이거나 더 이르게 일어난 이벤트를 받아도 예외 없이 상품 복사본이 그대로다")
    void keepReplicaForEventNotLater(long minutesFromStored) {
        replicaProductService.applyProductChanged(RecommendFixture.productAChangedEvent());

        assertThatCode(() -> replicaProductService.applyProductChanged(RecommendFixture.productAChangedEventAt(
            List.of(productOption(3L, "130", 125, 135)), PRODUCT_CHANGED_AT.plusMinutes(minutesFromStored))))
            .doesNotThrowAnyException();

        assertThat(replicaProductRepository.findById(PRODUCT_A_ID)).get()
            .extracting(RecommendReplicaProduct::getEventOccurredAt).isEqualTo(PRODUCT_CHANGED_AT);
        assertThat(sizesOf(PRODUCT_A_ID).sizeRanges())
            .containsExactlyInAnyOrder(SizeRange.of("110", 105, 115), SizeRange.of("120", 115, 125));
    }

    @Test
    @DisplayName("같은 상품 정보 변경됨을 여러 번 받아도 상품 복사본은 한 행이고 사이즈 줄이 늘지 않는다")
    void sameEventManyTimesKeepsOneReplica() {
        ProductChangedEvent event = RecommendFixture.productAChangedEvent();

        for (int i = 0; i < 3; i++) {
            replicaProductService.applyProductChanged(event);
        }

        assertThat(count("select count(*) from recommend.replica_product where product_id = ?")).isEqualTo(1);
        assertThat(count("select count(*) from recommend.replica_product_size where product_id = ?")).isEqualTo(2);
    }

    @Test
    @DisplayName("상품 정보 변경됨을 받으면 브랜드 · 설명 · 사이즈별 실측이 저장되고, 다시 읽어도 실측 값이 같다")
    void storeBrandDescriptionAndSizeSpecs() {
        replicaProductService.applyProductChanged(eventWithDetails(List.of(
            new ProductChangedEvent.SizeSpec("110", 105, 115,
                Map.of("TOTAL_LENGTH", new BigDecimal("42.5"), "CHEST_WIDTH", new BigDecimal("34"))),
            new ProductChangedEvent.SizeSpec("FREE", null, null, Map.of("HEAD_CIRCUMFERENCE", new BigDecimal("50")))),
            PRODUCT_CHANGED_AT));

        // 실측 줄은 지연 로딩이라 트랜잭션 안에서 읽는다. JSONB에서 읽은 숫자는 자릿수와 상관없이 값으로 비교한다
        RecommendReplicaProduct stored = transactionTemplate.execute(status -> {
            RecommendReplicaProduct product = replicaProductRepository.findById(PRODUCT_A_ID).orElseThrow();
            product.getSizeSpecs().size();
            return product;
        });
        assertThat(stored.getBrand()).isEqualTo("키들리");
        assertThat(stored.getDescription()).isEqualTo("상품 설명");
        assertThat(stored.getSizeSpecs())
            .usingRecursiveFieldByFieldElementComparator(RecursiveComparisonConfiguration.builder()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class).build())
            .containsExactlyInAnyOrder(
                new RecommendReplicaSizeSpec("110", 105, 115,
                    Map.of("TOTAL_LENGTH", new BigDecimal("42.5"), "CHEST_WIDTH", new BigDecimal("34"))),
                new RecommendReplicaSizeSpec("FREE", null, null, Map.of("HEAD_CIRCUMFERENCE", new BigDecimal("50"))));
    }

    @Test
    @DisplayName("INV-R01 더 늦게 일어난 이벤트를 받으면 사이즈별 실측 행이 새 사이즈로 바뀌고 옛 사이즈 행은 남지 않는다")
    void replaceSizeSpecRowsWithLaterEvent() {
        replicaProductService.applyProductChanged(eventWithDetails(List.of(
            new ProductChangedEvent.SizeSpec("110", 105, 115, Map.of("TOTAL_LENGTH", new BigDecimal("42"))),
            new ProductChangedEvent.SizeSpec("120", 115, 125, Map.of("TOTAL_LENGTH", new BigDecimal("45")))),
            PRODUCT_CHANGED_AT));

        replicaProductService.applyProductChanged(eventWithDetails(List.of(
            new ProductChangedEvent.SizeSpec("130", 125, 135, Map.of("TOTAL_LENGTH", new BigDecimal("48")))),
            LATER));

        assertThat(jdbcTemplate.queryForList(
            "select size from recommend.replica_product_size_spec where product_id = ?", String.class,
            PRODUCT_A_ID)).containsExactly("130");
    }

    @Test
    @DisplayName("브랜드 · 설명 · 사이즈별 실측이 없는 예전 상품 정보 변경됨을 받아도 복사본을 저장하고 세 값은 비워 둔다")
    void storeReplicaFromEventWithoutDetails() {
        replicaProductService.applyProductChanged(RecommendFixture.productAChangedEvent());

        assertThat(replicaProductRepository.findById(PRODUCT_A_ID)).get()
            .extracting(RecommendReplicaProduct::getBrand, RecommendReplicaProduct::getDescription)
            .containsExactly(null, null);
        assertThat(count("select count(*) from recommend.replica_product_size_spec where product_id = ?"))
            .isZero();
    }

    @Test
    @DisplayName("등록 취소 상태의 상품 정보 변경됨을 받으면 상품 복사본이 등록 취소 상태로 저장된다")
    void storeDelistedStatus() {
        ProductChangedEvent.Data data = RecommendFixture.productAChangedEvent().data();
        replicaProductService.applyProductChanged(new ProductChangedEvent(UUID.randomUUID(), PRODUCT_CHANGED_AT,
            new ProductChangedEvent.Data(data.productId(), data.sellerId(), data.name(), data.categoryId(),
                ProductStatus.DELISTED, data.thumbnailUrl(), data.createdAt(), data.sizeFree(), data.options())));

        assertThat(replicaProductRepository.findById(PRODUCT_A_ID)).get()
            .extracting(RecommendReplicaProduct::getStatus).isEqualTo(ProductStatus.DELISTED);
    }

    // 상품 A의 상품 정보 변경됨에 브랜드 · 설명 · 사이즈별 실측을 담는다. 옵션은 110 · 120이다
    private static ProductChangedEvent eventWithDetails(List<ProductChangedEvent.SizeSpec> sizeSpecs,
        LocalDateTime occurredAt) {
        ProductChangedEvent.Data data = RecommendFixture.productAChangedEvent().data();
        return new ProductChangedEvent(UUID.randomUUID(), occurredAt, new ProductChangedEvent.Data(
            data.productId(), data.sellerId(), data.name(), data.categoryId(), data.status(), data.thumbnailUrl(),
            data.createdAt(), data.sizeFree(), data.options(), data.infoValues(), "키들리", "상품 설명", sizeSpecs));
    }

    private long count(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class, PRODUCT_A_ID);
    }

    // 사이즈 줄은 지연 로딩이라 트랜잭션 안에서 읽는다. DB에서 읽은 순서는 정해지지 않아 테스트는 순서와 상관없이 비교한다
    private ProductSizes sizesOf(Long productId) {
        return transactionTemplate.execute(
            status -> replicaProductRepository.findById(productId).orElseThrow().toProductSizes());
    }
}
