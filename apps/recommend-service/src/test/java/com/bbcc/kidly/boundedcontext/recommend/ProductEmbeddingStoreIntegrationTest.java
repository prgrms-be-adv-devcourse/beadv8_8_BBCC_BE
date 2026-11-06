package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.port.embedding.ProductEmbeddingStore;
import com.bbcc.kidly.boundedcontext.recommend.app.port.embedding.ProductEmbeddingTarget;
import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaProductService;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 상품 임베딩 저장소가 실제 PostgreSQL(pgvector)에서 낡은 상품을 읽고 벡터를 저장하는 동작을 확인한다.
 */
@IntegrationTest
class ProductEmbeddingStoreIntegrationTest {

    private static final Long PRODUCT_1_ID = 31L;

    @Autowired
    private ProductEmbeddingStore store;

    @Autowired
    private RecommendReplicaProductService replicaProductService;

    @Test
    @DisplayName("임베딩할 글이 있고 벡터가 없는 상품을 낡은 대상으로 읽는다")
    void readProductWithoutVectorAsStale() {
        replicaProductService.applyProductChanged(productChangedEvent(PRODUCT_1_ID, "데일리 맨투맨"));

        List<ProductEmbeddingTarget> targets = store.findStale(10);

        assertThat(targets).containsExactly(new ProductEmbeddingTarget(PRODUCT_1_ID, "상품명: 데일리 맨투맨", false));
    }

    // 상품 A의 상품 정보 변경됨에서 상품 ID와 이름만 바꾼다
    private static ProductChangedEvent productChangedEvent(Long productId, String name) {
        ProductChangedEvent.Data data = RecommendFixture.productAChangedEvent().data();
        return new ProductChangedEvent(UUID.randomUUID(), RecommendFixture.PRODUCT_CHANGED_AT,
            new ProductChangedEvent.Data(productId, data.sellerId(), name, data.categoryId(), data.status(),
                data.thumbnailUrl(), data.createdAt(), data.sizeFree(), data.options()));
    }
}
