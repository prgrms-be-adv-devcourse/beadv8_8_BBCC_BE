package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.embedding.ProductEmbeddingService;
import com.bbcc.kidly.boundedcontext.recommend.app.port.embedding.ProductEmbedder;
import com.bbcc.kidly.boundedcontext.recommend.app.port.embedding.ProductEmbeddingStore;
import com.bbcc.kidly.boundedcontext.recommend.app.port.embedding.ProductEmbeddingTarget;
import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaProductService;
import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.EmbeddingVector;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 상품 임베딩 서비스가 실제 PostgreSQL(pgvector)의 상품 복사본을 읽고 벡터를 저장하는 흐름을 확인한다.
 * 테스트 컨텍스트에는 OpenAI 키가 없어 서비스 빈이 없으므로, 서비스를 직접 만들고 임베딩은 가짜로 바꾼다.
 */
@IntegrationTest
class ProductEmbeddingServiceIntegrationTest {

    private static final Long PRODUCT_1_ID = 41L;
    private static final Long PRODUCT_2_ID = 42L;
    private static final String PRODUCT_1_TEXT = "상품명: 데일리 맨투맨";
    private static final String PRODUCT_2_TEXT = "상품명: 데일리 후드티";

    @Autowired
    private ProductEmbeddingStore store;

    @Autowired
    private RecommendReplicaProductService replicaProductService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("글이 있는 복사본은 실행한 뒤 벡터가 저장돼 낡은 대상에서 빠진다")
    void storeVectorAndLeaveStaleList() {
        replicaProductService.applyProductChanged(productChangedEvent(PRODUCT_1_ID, "데일리 맨투맨"));
        List<List<String>> requests = new ArrayList<>();
        ProductEmbeddingService service = new ProductEmbeddingService(store, recordingEmbedder(requests));

        service.embedStaleProducts();

        assertThat(requests).containsExactly(List.of(PRODUCT_1_TEXT));
        assertThat(store.findStale(10)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
            "select embedding is not null from recommend.replica_product where product_id = ?",
            Boolean.class, PRODUCT_1_ID)).isTrue();
    }

    @Test
    @DisplayName("요청이 실패한 상품은 낡은 대상에 남고, 다음 실행에서 새 상품보다 뒤에 하나씩 다시 보내진다")
    void resendFailedProductAfterNewOnesOneByOne() {
        replicaProductService.applyProductChanged(productChangedEvent(PRODUCT_1_ID, "데일리 맨투맨"));
        ProductEmbeddingService failingService = new ProductEmbeddingService(store, texts -> {
            throw new IllegalStateException("임베딩 요청 실패");
        });
        failingService.embedStaleProducts();
        assertThat(store.findStale(10))
            .containsExactly(new ProductEmbeddingTarget(PRODUCT_1_ID, PRODUCT_1_TEXT, true));
        replicaProductService.applyProductChanged(productChangedEvent(PRODUCT_2_ID, "데일리 후드티"));
        List<List<String>> requests = new ArrayList<>();
        ProductEmbeddingService service = new ProductEmbeddingService(store, recordingEmbedder(requests));

        service.embedStaleProducts();

        assertThat(requests).containsExactly(List.of(PRODUCT_2_TEXT), List.of(PRODUCT_1_TEXT));
        assertThat(store.findStale(10)).isEmpty();
    }

    private static ProductEmbedder recordingEmbedder(List<List<String>> requests) {
        return texts -> {
            requests.add(texts);
            List<EmbeddingVector> vectors = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                vectors.add(vectorOf(0.5f));
            }
            return vectors;
        };
    }

    private static EmbeddingVector vectorOf(float value) {
        float[] values = new float[EmbeddingVector.DIMENSIONS];
        Arrays.fill(values, value);
        return new EmbeddingVector(values);
    }

    private static ProductChangedEvent productChangedEvent(Long productId, String name) {
        ProductChangedEvent.Data data = RecommendFixture.productAChangedEvent().data();
        return new ProductChangedEvent(UUID.randomUUID(), RecommendFixture.PRODUCT_CHANGED_AT,
            new ProductChangedEvent.Data(productId, data.sellerId(), name, data.categoryId(), data.status(),
                data.thumbnailUrl(), data.createdAt(), data.sizeFree(), data.options()));
    }
}
