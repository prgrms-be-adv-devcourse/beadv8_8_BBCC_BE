package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductSearchDocumentFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.product.ProductSearchDocumentFixture.PRODUCT_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductSearchDocumentFixture.changeInfo;
import static com.bbcc.kidly.boundedcontext.product.ProductSearchDocumentFixture.changeInfoAt;
import static com.bbcc.kidly.boundedcontext.product.ProductSearchDocumentFixture.option;
import static com.bbcc.kidly.boundedcontext.product.ProductSearchDocumentFixture.value;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.domain.search.ProductSearchDocument;
import com.bbcc.kidly.boundedcontext.product.domain.search.ProductSearchDocumentRepository;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 검색용 표 리포지토리가 늦게 일어난 이벤트로 만든 행만 남기는지 실제 PostgreSQL로 확인한다.
 * 리포지토리에는 트랜잭션이 없으므로 호출을 TransactionTemplate로 감싼다. 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
class ProductSearchDocumentRepositoryIntegrationTest {

    @Autowired
    private ProductSearchDocumentRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("처음 받은 상품이면 행을 만들고, 다시 읽은 attributes는 값 ID 목록 그대로이며 감사 칸도 채워진다")
    void savesNewProductAndReadsJsonbBack() {
        ProductSearchDocument document = ProductSearchDocument.create(changeInfo(
            List.of(option(25_000L, false, OptionSaleStatus.ON_SALE, value("COLOR", 5L), value("SIZE", 12L))),
            List.of(value("MAIN_MATERIAL", 20L))));

        boolean saved = transaction().execute(status -> repository.saveIfNewer(document));

        assertThat(saved).isTrue();
        ProductSearchDocument stored = transaction().execute(
            status -> entityManager.find(ProductSearchDocument.class, PRODUCT_ID));
        // 값 ID가 Integer로 읽히면 Long 목록과 같지 않아 실패한다
        assertThat(stored.getAttributes())
            .isEqualTo(Map.of("COLOR", List.of(5L), "SIZE", List.of(12L), "MAIN_MATERIAL", List.of(20L)));
        assertThat(stored).usingRecursiveComparison().ignoringFields("createdAt", "updatedAt").isEqualTo(document);
        assertThat(stored.getCreatedAt()).isNotNull();
        assertThat(stored.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("저장된 것보다 늦게 일어난 이벤트면 행 전체를 새 값으로 덮어쓴다")
    void newerEventReplacesRow() {
        save(documentAt(OCCURRED_AT, 25_000L, value("COLOR", 5L)));
        ProductSearchDocument newer = documentAt(OCCURRED_AT.plusMinutes(1), 19_000L, value("COLOR", 3L));

        boolean saved = transaction().execute(status -> repository.saveIfNewer(newer));

        assertThat(saved).isTrue();
        assertThat(stored()).usingRecursiveComparison().ignoringFields("createdAt", "updatedAt").isEqualTo(newer);
    }

    @ParameterizedTest(name = "저장된 시각에서 {0}분")
    @ValueSource(longs = {0, -1})
    @DisplayName("저장된 것과 같은 시각이나 더 이른 시각에 일어난 이벤트면 아무것도 바꾸지 않는다")
    void sameOrOlderEventChangesNothing(long minutes) {
        ProductSearchDocument first = documentAt(OCCURRED_AT, 25_000L, value("COLOR", 5L));
        save(first);
        LocalDateTime firstUpdatedAt = stored().getUpdatedAt();
        ProductSearchDocument late = documentAt(OCCURRED_AT.plusMinutes(minutes), 19_000L, value("COLOR", 3L));

        boolean saved = transaction().execute(status -> repository.saveIfNewer(late));

        assertThat(saved).isFalse();
        ProductSearchDocument stored = stored();
        assertThat(stored).usingRecursiveComparison().ignoringFields("createdAt", "updatedAt").isEqualTo(first);
        assertThat(stored.getUpdatedAt()).isEqualTo(firstUpdatedAt);
    }

    @RepeatedTest(20)
    @DisplayName("행이 없는 상품에 이른 이벤트와 늦은 이벤트를 동시에 저장해도 오류 없이 늦은 이벤트의 값이 남는다")
    void concurrentFirstSavesKeepNewerEvent() throws Exception {
        ProductSearchDocument older = documentAt(OCCURRED_AT, 25_000L, value("COLOR", 5L));
        ProductSearchDocument newer = documentAt(OCCURRED_AT.plusMinutes(1), 19_000L, value("COLOR", 3L));
        // 두 저장이 같은 순간에 시작하도록 맞춰, 둘 다 행이 없는 상태에서 INSERT하게 한다
        CyclicBarrier ready = new CyclicBarrier(2);
        List<Future<Boolean>> results;

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            results = executor.invokeAll(List.of(saveAfter(ready, older), saveAfter(ready, newer)));
        }

        assertThat(results).allSatisfy(result -> assertThat(result.state()).isEqualTo(Future.State.SUCCESS));
        assertThat(stored()).usingRecursiveComparison().ignoringFields("createdAt", "updatedAt").isEqualTo(newer);
    }

    private Callable<Boolean> saveAfter(CyclicBarrier ready, ProductSearchDocument document) {
        return () -> {
            await(ready);
            return transaction().execute(status -> repository.saveIfNewer(document));
        };
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException | BrokenBarrierException | TimeoutException e) {
            throw new IllegalStateException(e);
        }
    }

    // 옵션 하나(판매중, 재고 있음)와 그 옵션 값으로 만든 행이다. 이벤트 발생 시각과 가격 · 값만 바꿔 쓴다
    private static ProductSearchDocument documentAt(
        LocalDateTime occurredAt, long price, ProductSearchDocument.AttributeEntryInfo... values) {
        return ProductSearchDocument.create(changeInfoAt(occurredAt,
            List.of(option(price, false, OptionSaleStatus.ON_SALE, values)), List.of()));
    }

    private void save(ProductSearchDocument document) {
        transaction().executeWithoutResult(status -> repository.saveIfNewer(document));
    }

    private ProductSearchDocument stored() {
        return transaction().execute(status -> entityManager.find(ProductSearchDocument.class, PRODUCT_ID));
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }
}
