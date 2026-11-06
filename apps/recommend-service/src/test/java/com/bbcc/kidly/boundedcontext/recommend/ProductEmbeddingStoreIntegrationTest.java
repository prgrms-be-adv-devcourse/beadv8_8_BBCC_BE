package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.port.embedding.ProductEmbeddingStore;
import com.bbcc.kidly.boundedcontext.recommend.app.port.embedding.ProductEmbeddingTarget;
import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaProductService;
import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.EmbeddingVector;
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
    private static final Long PRODUCT_2_ID = 32L;

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

    @Test
    @DisplayName("벡터를 만든 글과 지금 글이 같으면 그 상품은 낡은 대상이 아니다")
    void upToDateProductIsNotStale() {
        replicaProductService.applyProductChanged(productChangedEvent(PRODUCT_1_ID, "데일리 맨투맨"));
        store.saveIfTextUnchanged(PRODUCT_1_ID, "상품명: 데일리 맨투맨", vectorOf(0.5f));

        assertThat(store.findStale(10)).isEmpty();
    }

    @Test
    @DisplayName("처음 보내는 상품을 앞서 보냈다가 성공하지 못한 상품보다 먼저 읽고, 다시 보내는 상품만 retried가 true다")
    void readNeverAttemptedProductBeforeRetriedOne() {
        replicaProductService.applyProductChanged(productChangedEvent(PRODUCT_1_ID, "데일리 맨투맨"));
        replicaProductService.applyProductChanged(productChangedEvent(PRODUCT_2_ID, "데일리 후드티"));
        store.markAttempted(List.of(PRODUCT_1_ID));

        assertThat(store.findStale(10)).containsExactly(
            new ProductEmbeddingTarget(PRODUCT_2_ID, "상품명: 데일리 후드티", false),
            new ProductEmbeddingTarget(PRODUCT_1_ID, "상품명: 데일리 맨투맨", true));
    }

    // 값이 서로 다른 벡터를 만든다. seed가 같으면 같은 벡터다
    private static EmbeddingVector vectorOf(float seed) {
        float[] values = new float[EmbeddingVector.DIMENSIONS];
        for (int i = 0; i < values.length; i++) {
            values[i] = seed + i / 1_000f;
        }
        return new EmbeddingVector(values);
    }

    // 상품 A의 상품 정보 변경됨에서 상품 ID와 이름만 바꾼다
    private static ProductChangedEvent productChangedEvent(Long productId, String name) {
        ProductChangedEvent.Data data = RecommendFixture.productAChangedEvent().data();
        return new ProductChangedEvent(UUID.randomUUID(), RecommendFixture.PRODUCT_CHANGED_AT,
            new ProductChangedEvent.Data(productId, data.sellerId(), name, data.categoryId(), data.status(),
                data.thumbnailUrl(), data.createdAt(), data.sizeFree(), data.options()));
    }
}
