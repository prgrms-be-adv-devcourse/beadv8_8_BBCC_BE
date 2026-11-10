package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.bbcc.kidly.boundedcontext.recommend.app.port.embedding.ProductEmbeddingStore;
import com.bbcc.kidly.boundedcontext.recommend.app.port.embedding.ProductEmbeddingTarget;
import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaProductService;
import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.EmbeddingVector;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProductRepository;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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

    @Autowired
    private RecommendReplicaProductRepository replicaProductRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    @Test
    @DisplayName("임베딩할 글이 null인 상품은 벡터가 있든 없든 낡은 대상이 아니다")
    void productWithoutTextIsNotStale() {
        replicaProductService.applyProductChanged(productChangedEvent(PRODUCT_1_ID, "데일리 맨투맨"));
        replicaProductService.applyProductChanged(productChangedEvent(PRODUCT_2_ID, "데일리 후드티"));
        store.saveIfTextUnchanged(PRODUCT_1_ID, "상품명: 데일리 맨투맨", vectorOf(0.5f));

        jdbcTemplate.update("update recommend.replica_product set embedding_text = null");

        assertThat(store.findStale(10)).isEmpty();
    }

    @Test
    @DisplayName("지금 글이 같으면 벡터를 저장하고 시도 시각을 지운다")
    void saveVectorAndClearAttemptedAtWhenTextUnchanged() {
        replicaProductService.applyProductChanged(productChangedEvent(PRODUCT_1_ID, "데일리 맨투맨"));
        store.markAttempted(List.of(PRODUCT_1_ID));
        EmbeddingVector vector = vectorOf(0.5f);

        boolean saved = store.saveIfTextUnchanged(PRODUCT_1_ID, "상품명: 데일리 맨투맨", vector);

        assertThat(saved).isTrue();
        assertThat(storedVector(PRODUCT_1_ID)).isEqualTo(vector);
        assertThat(attemptedAt(PRODUCT_1_ID)).isNull();
    }

    @Test
    @DisplayName("지금 글이 다르면 아무 행도 바꾸지 않고 false를 돌려준다")
    void saveNothingWhenTextChanged() {
        replicaProductService.applyProductChanged(productChangedEvent(PRODUCT_1_ID, "데일리 맨투맨"));
        store.markAttempted(List.of(PRODUCT_1_ID));

        boolean saved = store.saveIfTextUnchanged(PRODUCT_1_ID, "상품명: 옛 이름", vectorOf(0.5f));

        assertThat(saved).isFalse();
        assertThat(jdbcTemplate.queryForObject(
            "select embedding::text from recommend.replica_product where product_id = ?", String.class,
            PRODUCT_1_ID)).isNull();
        assertThat(attemptedAt(PRODUCT_1_ID)).isNotNull();
        assertThat(store.findStale(10)).hasSize(1);
    }

    @Test
    @DisplayName("벡터를 저장한 뒤 늦은 이벤트로 이름이 바뀌어도 저장된 벡터는 남고 그 상품은 다시 낡은 대상이 된다")
    void keepVectorWhenLaterEventChangesName() {
        replicaProductService.applyProductChanged(productChangedEvent(PRODUCT_1_ID, "데일리 맨투맨"));
        EmbeddingVector vector = vectorOf(0.5f);
        store.saveIfTextUnchanged(PRODUCT_1_ID, "상품명: 데일리 맨투맨", vector);

        replicaProductService.applyProductChanged(productChangedEvent(PRODUCT_1_ID, "새 맨투맨",
            RecommendFixture.PRODUCT_CHANGED_AT.plusMinutes(1)));

        assertThat(storedVector(PRODUCT_1_ID)).isEqualTo(vector);
        assertThat(store.findStale(10))
            .containsExactly(new ProductEmbeddingTarget(PRODUCT_1_ID, "상품명: 새 맨투맨", false));
    }

    @Test
    @DisplayName("벡터를 저장한 뒤 늦은 이벤트로 카테고리 경로만 바뀌어도 저장된 벡터는 남고 그 상품은 다시 낡은 대상이 된다")
    void keepVectorWhenLaterEventChangesOnlyCategoryPath() {
        replicaProductService.applyProductChanged(productChangedEvent(PRODUCT_1_ID, "데일리 맨투맨"));
        EmbeddingVector vector = vectorOf(0.5f);
        store.saveIfTextUnchanged(PRODUCT_1_ID, "상품명: 데일리 맨투맨", vector);

        replicaProductService.applyProductChanged(productChangedEventWithCategoryPath(PRODUCT_1_ID, "데일리 맨투맨",
            RecommendFixture.PRODUCT_CHANGED_AT.plusMinutes(1)));

        assertThat(storedVector(PRODUCT_1_ID)).isEqualTo(vector);
        assertThat(store.findStale(10)).containsExactly(new ProductEmbeddingTarget(
            PRODUCT_1_ID, "상품명: 데일리 맨투맨\n카테고리: 상의 > 티셔츠", false));
    }

    @Test
    @DisplayName("복사본을 읽은 트랜잭션이 끝나기 전에 벡터가 저장돼도 그 트랜잭션이 끝난 뒤 벡터와 만든 글 표시가 남고 그 상품은 다시 낡은 대상이 된다")
    void keepVectorSavedWhileReplicaTransactionIsOpen() {
        replicaProductService.applyProductChanged(productChangedEvent(PRODUCT_1_ID, "데일리 맨투맨"));
        EmbeddingVector vector = vectorOf(0.5f);
        ExecutorService otherThread = Executors.newSingleThreadExecutor();

        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                RecommendReplicaProduct product = replicaProductRepository.findById(PRODUCT_1_ID).orElseThrow();
                product.applyIfNewer(renamedChange("새 맨투맨"));

                Future<Boolean> saving = otherThread.submit(
                    () -> store.saveIfTextUnchanged(PRODUCT_1_ID, "상품명: 데일리 맨투맨", vector));

                assertThat(waitFor(saving)).isTrue();
            });
        } finally {
            otherThread.shutdownNow();
        }

        assertThat(storedVector(PRODUCT_1_ID)).isEqualTo(vector);
        assertThat(jdbcTemplate.queryForObject(
            "select embedded_text_md5 = md5('상품명: 데일리 맨투맨') from recommend.replica_product where product_id = ?",
            Boolean.class, PRODUCT_1_ID)).isTrue();
        assertThat(store.findStale(10))
            .containsExactly(new ProductEmbeddingTarget(PRODUCT_1_ID, "상품명: 새 맨투맨", false));
    }

    @Test
    @DisplayName("저장한 벡터끼리 recommend 스키마의 코사인 거리 연산자로 거리를 잰다")
    void measureDistanceBetweenStoredVectors() {
        replicaProductService.applyProductChanged(productChangedEvent(PRODUCT_1_ID, "데일리 맨투맨"));
        replicaProductService.applyProductChanged(productChangedEvent(PRODUCT_2_ID, "데일리 후드티"));
        store.saveIfTextUnchanged(PRODUCT_1_ID, "상품명: 데일리 맨투맨", vectorOf(0.5f));
        store.saveIfTextUnchanged(PRODUCT_2_ID, "상품명: 데일리 후드티", vectorOf(-3f));

        assertThat(distance(PRODUCT_1_ID, PRODUCT_1_ID)).isCloseTo(0.0, within(1e-6));
        assertThat(distance(PRODUCT_1_ID, PRODUCT_2_ID)).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("vector 확장은 recommend 스키마에 있다")
    void vectorExtensionIsInRecommendSchema() {
        assertThat(jdbcTemplate.queryForList("""
            select n.nspname from pg_extension e join pg_namespace n on n.oid = e.extnamespace
            where e.extname = 'vector'
            """, String.class)).containsExactly("recommend");
    }

    private EmbeddingVector storedVector(Long productId) {
        String text = jdbcTemplate.queryForObject(
            "select embedding::text from recommend.replica_product where product_id = ?", String.class, productId);
        String[] tokens = text.substring(1, text.length() - 1).split(",");
        float[] values = new float[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            values[i] = Float.parseFloat(tokens[i]);
        }
        return new EmbeddingVector(values);
    }

    private LocalDateTime attemptedAt(Long productId) {
        return jdbcTemplate.queryForObject(
            "select embedding_attempted_at from recommend.replica_product where product_id = ?",
            LocalDateTime.class, productId);
    }

    private double distance(Long productId, Long otherProductId) {
        return jdbcTemplate.queryForObject("""
            select a.embedding OPERATOR(recommend.<=>) b.embedding
            from recommend.replica_product a, recommend.replica_product b
            where a.product_id = ? and b.product_id = ?
            """, Double.class, productId, otherProductId);
    }

    private static <T> T waitFor(Future<T> future) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException(e);
        }
    }

    private static RecommendReplicaProduct.ChangeInfo renamedChange(String name) {
        RecommendReplicaProduct.ChangeInfo base = RecommendFixture.productAChange();
        return new RecommendReplicaProduct.ChangeInfo(PRODUCT_1_ID, base.sellerId(), base.categoryId(), name,
            base.status(), base.thumbnailUrl(), base.registeredAt(), base.sizeFree(), base.options(), null, null,
            List.of(), List.of(), RecommendFixture.PRODUCT_CHANGED_AT.plusMinutes(1));
    }

    private static EmbeddingVector vectorOf(float seed) {
        float[] values = new float[EmbeddingVector.DIMENSIONS];
        for (int i = 0; i < values.length; i++) {
            values[i] = seed + i / 1_000f;
        }
        return new EmbeddingVector(values);
    }

    private static ProductChangedEvent productChangedEvent(Long productId, String name) {
        return productChangedEvent(productId, name, RecommendFixture.PRODUCT_CHANGED_AT);
    }

    private static ProductChangedEvent productChangedEvent(Long productId, String name, LocalDateTime occurredAt) {
        ProductChangedEvent.Data data = RecommendFixture.productAChangedEvent().data();
        return new ProductChangedEvent(UUID.randomUUID(), occurredAt,
            new ProductChangedEvent.Data(productId, data.sellerId(), name, data.categoryId(), data.status(),
                data.thumbnailUrl(), data.createdAt(), data.sizeFree(), data.options()));
    }

    private static ProductChangedEvent productChangedEventWithCategoryPath(
        Long productId, String name, LocalDateTime occurredAt) {
        ProductChangedEvent.Data data = RecommendFixture.productAChangedEvent().data();
        return new ProductChangedEvent(UUID.randomUUID(), occurredAt,
            new ProductChangedEvent.Data(productId, data.sellerId(), name, data.categoryId(), data.status(),
                data.thumbnailUrl(), data.createdAt(), data.sizeFree(), data.options(), List.of(), null, null,
                List.of(), List.of(new ProductChangedEvent.CategoryPathEntry(1L, "상의"),
                    new ProductChangedEvent.CategoryPathEntry(11L, "티셔츠"))));
    }
}
