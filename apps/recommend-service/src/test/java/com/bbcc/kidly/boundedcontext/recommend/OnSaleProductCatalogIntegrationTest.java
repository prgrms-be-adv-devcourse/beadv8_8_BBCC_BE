package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_BRAND;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_CATEGORY_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_DESCRIPTION;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_NAME;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_REGISTERED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_SELLER_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_THUMBNAIL_URL;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_CHANGED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.productAInfoValues;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.productASizeSpecInfos;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.productOptionInfo;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.recommended.support.OnSaleProductCatalog;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProductRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.ReplicaProductSnapshot;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 메모리에 보관한 판매중 상품이 실제 DB의 상품 복사본과 맞춰지는지 확인한다.
 * 테스트 설정은 확인 간격이 0초라 읽을 때마다 DB와 맞춘다. 보관소는 테스트끼리 함께 쓰는 빈이라 앞 테스트의 값이 남아 있을 수 있다.
 */
@IntegrationTest
class OnSaleProductCatalogIntegrationTest {

    private static final Long FIRST_PRODUCT_ID = 81L;
    private static final Long SECOND_PRODUCT_ID = 82L;

    @Autowired
    private OnSaleProductCatalog catalog;

    @Autowired
    private RecommendReplicaProductRepository replicaProductRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    @DisplayName("저장된 판매중 상품 복사본을 트랜잭션 밖에서 읽어도 사이즈와 판매 가능 사이즈까지 옮겨 담는다")
    void loadOnSaleProductsOutsideTransaction() {
        replicaProductRepository.save(RecommendFixture.replicaProduct(FIRST_PRODUCT_ID, ProductStatus.ON_SALE));
        replicaProductRepository.save(RecommendFixture.replicaProduct(SECOND_PRODUCT_ID, ProductStatus.STOPPED));

        List<ReplicaProductSnapshot> products = catalog.onSale(null);

        assertThat(products).extracting(ReplicaProductSnapshot::productId).containsExactly(FIRST_PRODUCT_ID);
        ReplicaProductSnapshot product = products.get(0);
        assertThat(product.sizes()).isEqualTo(RecommendFixture.productA());
        assertThat(product.isSellable("110")).isTrue();
        assertThat(product.updatedAt()).isNotNull();
    }

    @Test
    @DisplayName("판매중이던 상품 복사본이 판매 중지로 바뀌면 다음에 읽을 때 빠진다")
    void removeProductThatStoppedSelling() {
        replicaProductRepository.save(RecommendFixture.replicaProduct(FIRST_PRODUCT_ID, ProductStatus.ON_SALE));
        replicaProductRepository.save(RecommendFixture.replicaProduct(SECOND_PRODUCT_ID, ProductStatus.ON_SALE));
        assertThat(catalog.onSale(null)).extracting(ReplicaProductSnapshot::productId)
            .containsExactlyInAnyOrder(FIRST_PRODUCT_ID, SECOND_PRODUCT_ID);

        transactionTemplate.executeWithoutResult(status -> replicaProductRepository.findById(SECOND_PRODUCT_ID)
            .orElseThrow()
            .applyIfNewer(stopped(SECOND_PRODUCT_ID)));

        assertThat(catalog.onSale(null)).extracting(ReplicaProductSnapshot::productId)
            .containsExactly(FIRST_PRODUCT_ID);
    }

    @Test
    @DisplayName("표가 비워진 뒤 다른 상품 복사본이 저장되면 앞서 보관한 상품은 남지 않는다")
    void dropProductsThatNoLongerExist() {
        replicaProductRepository.save(RecommendFixture.replicaProduct(FIRST_PRODUCT_ID, ProductStatus.ON_SALE));
        assertThat(catalog.onSale(null)).extracting(ReplicaProductSnapshot::productId)
            .containsExactly(FIRST_PRODUCT_ID);
        // 테스트 사이에 표를 비우는 것과 같은 일을 한다. 지워진 행은 고쳐진 상품 복사본으로 읽히지 않는다
        jdbcClient.sql("TRUNCATE TABLE recommend.replica_product CASCADE").update();

        replicaProductRepository.save(RecommendFixture.replicaProduct(SECOND_PRODUCT_ID, ProductStatus.ON_SALE));

        assertThat(catalog.onSale(null)).extracting(ReplicaProductSnapshot::productId)
            .containsExactly(SECOND_PRODUCT_ID);
    }

    // 먼저 저장한 복사본보다 늦게 일어난 판매 중지 변경이다
    private static RecommendReplicaProduct.ChangeInfo stopped(Long productId) {
        return new RecommendReplicaProduct.ChangeInfo(productId, PRODUCT_A_SELLER_ID, PRODUCT_A_CATEGORY_ID,
            PRODUCT_A_NAME, ProductStatus.STOPPED, PRODUCT_A_THUMBNAIL_URL, PRODUCT_A_REGISTERED_AT, false,
            List.of(productOptionInfo("110", 105, 115), productOptionInfo("120", 115, 125)), PRODUCT_A_BRAND,
            PRODUCT_A_DESCRIPTION, productASizeSpecInfos(), productAInfoValues(),
            PRODUCT_CHANGED_AT.plusMinutes(1));
    }
}
