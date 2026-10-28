package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_CATEGORY_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProductRepository;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.support.IntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 자녀 맞춤 목록이 쓸 상품 복사본 조회 쿼리를 확인한다.
 * 저장한 행이 커밋된 뒤에 읽도록 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
class RecommendReplicaProductQueryIntegrationTest {

    // 판매 상태마다 다른 상품 ID를 써서, 어느 복사본이 읽혔는지 ID로 구분한다
    private static final Long ON_SALE_PRODUCT_ID = 61L;

    private static final Long SALE_PENDING_PRODUCT_ID = 62L;

    private static final Long STOPPED_PRODUCT_ID = 63L;

    private static final Long OTHER_CATEGORY_PRODUCT_ID = 64L;

    // 상품 A의 카테고리와 다른 카테고리다. 상품 ID와도 겹치지 않는 값을 쓴다
    private static final Long OTHER_CATEGORY_ID = 53L;

    // 상품 하나마다 SQL이 늘면 차이가 드러나도록 상품 복사본을 여러 개 저장한다
    private static final int MANY = 5;

    private static final Long FIRST_MANY_PRODUCT_ID = 71L;

    @Autowired
    private RecommendReplicaProductRepository replicaProductRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("판매중인 상품 복사본만 읽고 판매 시작 전이거나 판매 중지된 상품 복사본은 읽지 않는다")
    void findOnlyOnSaleProducts() {
        replicaProductRepository.save(RecommendFixture.replicaProduct(ON_SALE_PRODUCT_ID, ProductStatus.ON_SALE));
        replicaProductRepository.save(
            RecommendFixture.replicaProduct(SALE_PENDING_PRODUCT_ID, ProductStatus.SALE_PENDING));
        replicaProductRepository.save(RecommendFixture.replicaProduct(STOPPED_PRODUCT_ID, ProductStatus.STOPPED));

        assertThat(findAllOnSale(null))
            .extracting(RecommendReplicaProduct::getProductId)
            .containsExactly(ON_SALE_PRODUCT_ID);
    }

    @Test
    @DisplayName("카테고리를 주면 카테고리가 같은 판매중 상품 복사본만 읽고 카테고리가 다르거나 판매 시작 전인 상품 복사본은 읽지 않는다")
    void findOnSaleProductsInCategory() {
        saveOnSaleProductsInTwoCategories();
        replicaProductRepository.save(RecommendFixture.replicaProduct(
            SALE_PENDING_PRODUCT_ID, ProductStatus.SALE_PENDING, PRODUCT_A_CATEGORY_ID));

        assertThat(findAllOnSale(PRODUCT_A_CATEGORY_ID))
            .extracting(RecommendReplicaProduct::getProductId)
            .containsExactly(ON_SALE_PRODUCT_ID);
    }

    @Test
    @DisplayName("카테고리를 주지 않으면 카테고리와 상관없이 판매중인 상품 복사본을 모두 읽는다")
    void findOnSaleProductsInAllCategories() {
        saveOnSaleProductsInTwoCategories();

        assertThat(findAllOnSale(null))
            .extracting(RecommendReplicaProduct::getProductId)
            .containsExactlyInAnyOrder(ON_SALE_PRODUCT_ID, OTHER_CATEGORY_PRODUCT_ID);
    }

    @Test
    @DisplayName("판매중인 상품 복사본을 읽고 사이즈 줄과 판매 가능 사이즈까지 써도 상품이 100개까지는 상품 수와 상관없이 SQL이 3개다")
    void loadSizesWithoutQueryPerProduct() {
        for (int i = 0; i < MANY; i++) {
            replicaProductRepository.save(
                RecommendFixture.replicaProduct(FIRST_MANY_PRODUCT_ID + i, ProductStatus.ON_SALE));
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean statisticsWasEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();

            transactionTemplate.executeWithoutResult(status -> {
                List<RecommendReplicaProduct> products = replicaProductRepository.findAllOnSale(null);
                assertThat(products).hasSize(MANY);
                // 지연 로딩 컬렉션을 모두 써서, 컬렉션을 읽는 SQL까지 개수에 넣는다
                for (RecommendReplicaProduct product : products) {
                    assertThat(product.toProductSizes().sizeRanges()).hasSize(2);
                    assertThat(product.hasSellableOption()).isTrue();
                    assertThat(product.isSellable("110")).isTrue();
                }
            });

            assertThat(statistics.getPrepareStatementCount())
                .as("상품 조회 1개 + 사이즈 줄 묶음 조회 1개 + 판매 가능 사이즈 묶음 조회 1개")
                .isEqualTo(3);
        } finally {
            statistics.setStatisticsEnabled(statisticsWasEnabled);
        }
    }

    // 상품 A의 카테고리와 다른 카테고리에 판매중 상품 복사본을 하나씩 저장한다
    private void saveOnSaleProductsInTwoCategories() {
        replicaProductRepository.save(RecommendFixture.replicaProduct(
            ON_SALE_PRODUCT_ID, ProductStatus.ON_SALE, PRODUCT_A_CATEGORY_ID));
        replicaProductRepository.save(RecommendFixture.replicaProduct(
            OTHER_CATEGORY_PRODUCT_ID, ProductStatus.ON_SALE, OTHER_CATEGORY_ID));
    }

    // 직접 선언한 쿼리 메서드에는 트랜잭션이 붙지 않으므로, 서비스가 부르는 것처럼 트랜잭션 안에서 읽는다
    private List<RecommendReplicaProduct> findAllOnSale(Long categoryId) {
        return transactionTemplate.execute(status -> replicaProductRepository.findAllOnSale(categoryId));
    }
}
