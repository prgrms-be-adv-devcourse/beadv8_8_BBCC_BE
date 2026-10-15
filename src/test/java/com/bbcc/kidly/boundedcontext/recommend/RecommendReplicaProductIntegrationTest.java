package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_CATEGORY_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_NAME;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_REGISTERED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_SELLER_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_THUMBNAIL_URL;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_CHANGED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.productOption;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaProductService;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProductRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.ProductSizes;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRange;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 상품 정보 변경됨을 받아 추천의 상품 복사본에 반영하는 흐름을 확인한다.
 * 리스너는 발행한 트랜잭션이 커밋된 뒤에 실행되므로 테스트에 @Transactional을 붙이지 않고 발행만 트랜잭션 안에서 한다.
 */
@IntegrationTest
class RecommendReplicaProductIntegrationTest {

    private static final LocalDateTime LATER = PRODUCT_CHANGED_AT.plusMinutes(1);

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private RecommendReplicaProductRepository replicaProductRepository;

    // 리스너의 예외는 커밋 뒤라 로그로만 남으므로, 반영 규칙은 서비스를 직접 불러 예외까지 확인한다
    @Autowired
    private RecommendReplicaProductService replicaProductService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("상품 정보 변경됨을 발행한 트랜잭션이 커밋되면 이벤트 값이 상품 복사본에 저장된다")
    void saveReplicaAfterCommit() {
        publishInTransaction(RecommendFixture.productAChangedEvent());

        RecommendReplicaProduct replica = replicaProductRepository.findById(PRODUCT_A_ID).orElseThrow();
        assertThat(replica)
            .extracting(
                RecommendReplicaProduct::getSellerId,
                RecommendReplicaProduct::getCategoryId,
                RecommendReplicaProduct::getStatus,
                RecommendReplicaProduct::getThumbnailUrl,
                RecommendReplicaProduct::getProductRegisteredAt,
                RecommendReplicaProduct::isSizeFree,
                RecommendReplicaProduct::getEventOccurredAt)
            .containsExactly(PRODUCT_A_SELLER_ID, PRODUCT_A_CATEGORY_ID, ProductStatus.ON_SALE,
                PRODUCT_A_THUMBNAIL_URL, PRODUCT_A_REGISTERED_AT, false, PRODUCT_CHANGED_AT);
        // 하한 · 상한이 서로 다른 값이라, 저장할 때 두 칸이 바뀌면 다시 읽은 값이 달라진다
        assertThat(sizesOf(PRODUCT_A_ID).sizeRanges())
            .containsExactlyInAnyOrder(SizeRange.of("110", 105, 115), SizeRange.of("120", 115, 125));
    }

    // 가장 낮은 판매가를 판매 가능한 옵션에 두어, 품절 · 판매 중지 옵션을 최저가에 넣을지와 상관없이 기대값이 하나로 정해진다
    @Test
    @DisplayName("상품 정보 변경됨을 받으면 상품명 · 최저가 · 판매 가능 사이즈가 상품 복사본에 저장된다")
    void saveNamePriceAndSellableSizes() {
        publishInTransaction(RecommendFixture.productAChangedEventAt(List.of(
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
        publishInTransaction(RecommendFixture.productAChangedEventOf(
            true, List.of(productOption(3L, "FREE", null, null)), PRODUCT_CHANGED_AT));

        ProductSizes sizes = sizesOf(PRODUCT_A_ID);
        assertThat(sizes.isSizeFree()).isTrue();
        assertThat(sizes.sizeRanges()).isEmpty();
    }

    @Test
    @DisplayName("상품 정보 변경됨을 발행한 트랜잭션이 롤백되면 상품 복사본이 생기지 않는다")
    void noReplicaAfterRollback() {
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(RecommendFixture.productAChangedEvent());
            status.setRollbackOnly();
        });

        assertThat(replicaProductRepository.findById(PRODUCT_A_ID)).isEmpty();
    }

    @Test
    @DisplayName("상품 복사본 반영이 실패해도 발행한 트랜잭션은 성공하고 복사본은 이전 값 그대로다")
    void keepReplicaWhenListenerFails() {
        publishInTransaction(RecommendFixture.productAChangedEvent());
        ProductChangedEvent withoutSize = RecommendFixture.productAChangedEventAt(
            List.of(productOption(3L, null, 125, 135)), LATER);

        assertThatCode(() -> publishInTransaction(withoutSize)).doesNotThrowAnyException();

        assertThat(replicaProductRepository.findById(PRODUCT_A_ID)).get()
            .extracting(RecommendReplicaProduct::getEventOccurredAt).isEqualTo(PRODUCT_CHANGED_AT);
        assertThat(sizesOf(PRODUCT_A_ID).sizeRanges())
            .containsExactlyInAnyOrder(SizeRange.of("110", 105, 115), SizeRange.of("120", 115, 125));
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
            publishInTransaction(event);
        }

        assertThat(count("select count(*) from recommend.replica_product where product_id = ?")).isEqualTo(1);
        assertThat(count("select count(*) from recommend.replica_product_size where product_id = ?")).isEqualTo(2);
    }

    private long count(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class, PRODUCT_A_ID);
    }

    // 사이즈 줄은 지연 로딩이라 트랜잭션 안에서 읽는다. DB에서 읽은 순서는 정해지지 않아 테스트는 순서와 상관없이 비교한다
    private ProductSizes sizesOf(Long productId) {
        return transactionTemplate.execute(
            status -> replicaProductRepository.findById(productId).orElseThrow().toProductSizes());
    }

    private void publishInTransaction(ProductChangedEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }
}
