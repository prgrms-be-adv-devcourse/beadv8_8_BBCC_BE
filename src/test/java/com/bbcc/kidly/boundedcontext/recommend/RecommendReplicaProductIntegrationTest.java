package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_CATEGORY_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_REGISTERED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_SELLER_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_THUMBNAIL_URL;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_CHANGED_AT;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProductRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.ProductSizes;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRange;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 상품 정보 변경됨을 받아 추천의 상품 복사본에 반영하는 흐름을 확인한다.
 * 리스너는 발행한 트랜잭션이 커밋된 뒤에 실행되므로 테스트에 @Transactional을 붙이지 않고 발행만 트랜잭션 안에서 한다.
 */
@IntegrationTest
class RecommendReplicaProductIntegrationTest {

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private RecommendReplicaProductRepository replicaProductRepository;

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

    // 사이즈 줄은 지연 로딩이라 트랜잭션 안에서 읽는다. DB에서 읽은 순서는 정해지지 않아 테스트는 순서와 상관없이 비교한다
    private ProductSizes sizesOf(Long productId) {
        return transactionTemplate.execute(
            status -> replicaProductRepository.findById(productId).orElseThrow().toProductSizes());
    }

    private void publishInTransaction(ProductChangedEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }
}
