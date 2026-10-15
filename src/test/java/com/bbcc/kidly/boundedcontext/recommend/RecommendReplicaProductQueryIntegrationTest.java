package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProductRepository;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.List;
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

    @Autowired
    private RecommendReplicaProductRepository replicaProductRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

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

    // 직접 선언한 쿼리 메서드에는 트랜잭션이 붙지 않으므로, 서비스가 부르는 것처럼 트랜잭션 안에서 읽는다
    private List<RecommendReplicaProduct> findAllOnSale(Long categoryId) {
        return transactionTemplate.execute(status -> replicaProductRepository.findAllOnSale(categoryId));
    }
}
