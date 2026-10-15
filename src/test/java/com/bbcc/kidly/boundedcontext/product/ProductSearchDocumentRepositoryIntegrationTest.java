package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductSearchDocumentFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.product.ProductSearchDocumentFixture.PRODUCT_CREATED_AT;
import static com.bbcc.kidly.boundedcontext.product.ProductSearchDocumentFixture.PRODUCT_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductSearchDocumentFixture.changeInfo;
import static com.bbcc.kidly.boundedcontext.product.ProductSearchDocumentFixture.changeInfoAt;
import static com.bbcc.kidly.boundedcontext.product.ProductSearchDocumentFixture.option;
import static com.bbcc.kidly.boundedcontext.product.ProductSearchDocumentFixture.value;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.domain.search.ProductSearchDocument;
import com.bbcc.kidly.boundedcontext.product.domain.search.ProductSearchDocumentRepository;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.time.Duration;
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

    // 저장하는 시각이다. DB에 마이크로초까지만 남으므로 초 단위로 둔다
    private static final LocalDateTime FIRST_SAVED_AT = LocalDateTime.of(2026, 10, 15, 18, 0);
    private static final LocalDateTime SECOND_SAVED_AT = LocalDateTime.of(2026, 10, 15, 18, 5);

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("처음 받은 상품이면 행을 만들고, 다시 읽은 attributes는 값 ID 목록 그대로이며 감사 칸은 저장한 시각이다")
    void savesNewProductAndReadsJsonbBack() {
        ProductSearchDocument document = ProductSearchDocument.create(changeInfo(
            List.of(option(25_000L, false, OptionSaleStatus.ON_SALE, value("COLOR", 5L), value("SIZE", 12L))),
            List.of(value("MAIN_MATERIAL", 20L))));

        boolean saved = saveIfNewer(document, FIRST_SAVED_AT);

        assertThat(saved).isTrue();
        ProductSearchDocument stored = stored();
        // 값 ID가 Integer로 읽히면 Long 목록과 같지 않아 실패한다
        assertThat(stored.getAttributes())
            .isEqualTo(Map.of("COLOR", List.of(5L), "SIZE", List.of(12L), "MAIN_MATERIAL", List.of(20L)));
        assertThat(stored).usingRecursiveComparison().ignoringFields("createdAt", "updatedAt").isEqualTo(document);
        assertThat(stored.getCreatedAt()).isEqualTo(FIRST_SAVED_AT);
        assertThat(stored.getUpdatedAt()).isEqualTo(FIRST_SAVED_AT);
    }

    @Test
    @DisplayName("저장된 것보다 늦게 일어난 이벤트면 등록 시각만 두고 모든 칸을 새 값으로 덮어쓴다")
    void newerEventReplacesRow() {
        saveIfNewer(documentAt(OCCURRED_AT, 25_000L, value("COLOR", 5L)), FIRST_SAVED_AT);
        // 상품 ID 말고는 모든 칸이 처음 행과 다른 값이다. 덮어쓰는 SQL에서 칸 하나를 빠뜨려도 잡도록 한다
        ProductSearchDocument newer = ProductSearchDocument.create(new ProductSearchDocument.ChangeInfo(
            PRODUCT_ID, 8L, "상품 A 새 이름", 4L, ProductStatus.STOPPED, null, PRODUCT_CREATED_AT.plusDays(1), true,
            List.of(option(19_000L, true, OptionSaleStatus.ON_SALE, value("COLOR", 3L))),
            List.of(value("MAIN_MATERIAL", 21L)), OCCURRED_AT.plusMinutes(1)));

        boolean saved = saveIfNewer(newer, SECOND_SAVED_AT);

        assertThat(saved).isTrue();
        ProductSearchDocument stored = stored();
        assertThat(stored).usingRecursiveComparison().ignoringFields("createdAt", "updatedAt").isEqualTo(newer);
        assertThat(stored.getCreatedAt()).isEqualTo(FIRST_SAVED_AT);
        assertThat(stored.getUpdatedAt()).isEqualTo(SECOND_SAVED_AT);
    }

    @ParameterizedTest(name = "저장된 시각에서 {0}분")
    @ValueSource(longs = {0, -1})
    @DisplayName("저장된 것과 같은 시각이나 더 이른 시각에 일어난 이벤트면 아무것도 바꾸지 않는다")
    void sameOrOlderEventChangesNothing(long minutes) {
        ProductSearchDocument first = documentAt(OCCURRED_AT, 25_000L, value("COLOR", 5L));
        saveIfNewer(first, FIRST_SAVED_AT);
        ProductSearchDocument late = documentAt(OCCURRED_AT.plusMinutes(minutes), 19_000L, value("COLOR", 3L));

        boolean saved = saveIfNewer(late, SECOND_SAVED_AT);

        assertThat(saved).isFalse();
        ProductSearchDocument stored = stored();
        assertThat(stored).usingRecursiveComparison().ignoringFields("createdAt", "updatedAt").isEqualTo(first);
        assertThat(stored.getUpdatedAt()).isEqualTo(FIRST_SAVED_AT);
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

        // 실패하면 그 예외를 보여 준다. 늦은 이벤트는 어느 쪽이 먼저 저장되든 반영된다
        assertThat(results.get(0)).succeedsWithin(Duration.ofSeconds(10));
        assertThat(results.get(1)).succeedsWithin(Duration.ofSeconds(10)).isEqualTo(true);
        assertThat(stored()).usingRecursiveComparison().ignoringFields("createdAt", "updatedAt").isEqualTo(newer);
    }

    private Callable<Boolean> saveAfter(CyclicBarrier ready, ProductSearchDocument document) {
        return () -> {
            await(ready);
            return saveIfNewer(document, FIRST_SAVED_AT);
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

    private boolean saveIfNewer(ProductSearchDocument document, LocalDateTime now) {
        return transaction().execute(status -> repository.saveIfNewer(document, now));
    }

    private ProductSearchDocument stored() {
        return transaction().execute(status -> entityManager.find(ProductSearchDocument.class, PRODUCT_ID));
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }
}
