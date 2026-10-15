package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductSearchDocumentFixture.CATEGORY_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductSearchDocumentFixture.NAME;
import static com.bbcc.kidly.boundedcontext.product.ProductSearchDocumentFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.product.ProductSearchDocumentFixture.PRODUCT_CREATED_AT;
import static com.bbcc.kidly.boundedcontext.product.ProductSearchDocumentFixture.PRODUCT_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductSearchDocumentFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductSearchDocumentFixture.THUMBNAIL_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.bbcc.kidly.boundedcontext.product.domain.search.ProductSearchDocument;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 상품 정보 변경됨을 발행한 트랜잭션이 커밋된 뒤, 상품의 검색 쪽 리스너가 검색용 표 행을 다시 만드는지 실제 PostgreSQL로 확인한다.
 * 리스너는 커밋 뒤 발행한 스레드에서 새 트랜잭션으로 돌므로, 발행이 끝나면 결과를 바로 읽을 수 있다. 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
class ProductSearchDocumentEventIntegrationTest {

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("상품 정보 변경됨을 발행한 트랜잭션이 커밋되면 이벤트 값으로 검색용 표 행이 생긴다")
    void createsRowAfterCommit() {
        // 120 옵션은 더 싸지만 품절이라 표시 가격은 110 옵션의 25,000원이다. 혼용률은 직접 입력이라 값 ID가 없다
        publishInTransaction(eventAt(OCCURRED_AT, List.of(
            option(11L, 25_000L, false, entry("COLOR", 5L, "네이비"), entry("SIZE", 12L, "110")),
            option(12L, 22_000L, true, entry("COLOR", 5L, "네이비"), entry("SIZE", 13L, "120")))));

        ProductSearchDocument stored = stored();
        assertThat(stored).isNotNull();
        assertThat(stored)
            .extracting(ProductSearchDocument::getName, ProductSearchDocument::getThumbnailUrl,
                ProductSearchDocument::getSellerId, ProductSearchDocument::getCategoryId,
                ProductSearchDocument::getStatus, ProductSearchDocument::isSizeFree,
                ProductSearchDocument::getProductCreatedAt, ProductSearchDocument::getEventOccurredAt,
                ProductSearchDocument::getMinPrice, ProductSearchDocument::isSoldOut)
            .containsExactly(NAME, THUMBNAIL_URL, SELLER_ID, CATEGORY_ID, ProductStatus.ON_SALE, true,
                PRODUCT_CREATED_AT, OCCURRED_AT, 25_000L, false);
        assertThat(stored.getAttributes())
            .isEqualTo(Map.of("COLOR", List.of(5L), "SIZE", List.of(12L, 13L), "MAIN_MATERIAL", List.of(20L)));
    }

    @Test
    @DisplayName("상품 정보 변경됨을 발행한 트랜잭션이 롤백되면 검색용 표 행이 생기지 않는다")
    void noRowWhenPublisherRollsBack() {
        transaction().executeWithoutResult(status -> {
            eventPublisher.publish(eventAt(OCCURRED_AT, List.of(option(11L, 25_000L, false))));
            status.setRollbackOnly();
        });

        assertThat(stored()).isNull();
    }

    @Test
    @DisplayName("같은 상품 정보 변경됨을 두 번 받아도 한 번만 반영하고, 두 번째에는 행을 고치지 않는다")
    void sameEventTwiceAppliesOnce() {
        ProductChangedEvent event = eventAt(OCCURRED_AT,
            List.of(option(11L, 25_000L, false, entry("COLOR", 5L, "네이비"))));
        publishInTransaction(event);
        ProductSearchDocument first = stored();

        publishInTransaction(event);

        ProductSearchDocument second = stored();
        assertThat(second).usingRecursiveComparison().isEqualTo(first);
    }

    @Test
    @DisplayName("SALE-07 행이 있는 상품에 더 늦게 일어난 이벤트가 오면 새 값으로 덮어써, 마지막 옵션이 품절되면 품절로 바뀐다")
    void newerEventReplacesRowAndMarksSoldOut() {
        publishInTransaction(eventAt(OCCURRED_AT, List.of(option(11L, 25_000L, false), option(12L, 22_000L, true))));

        // 110 옵션까지 품절되면 모든 옵션이 품절이라, 표시 가격은 전체 옵션 중 최저가인 22,000원이 된다
        publishInTransaction(eventAt(OCCURRED_AT.plusMinutes(1),
            List.of(option(11L, 25_000L, true), option(12L, 22_000L, true))));

        assertThat(stored())
            .extracting(ProductSearchDocument::isSoldOut, ProductSearchDocument::getMinPrice,
                ProductSearchDocument::getEventOccurredAt)
            .containsExactly(true, 22_000L, OCCURRED_AT.plusMinutes(1));
    }

    @Test
    @DisplayName("늦게 일어난 이벤트를 먼저 받은 뒤 더 이르게 일어난 이벤트가 늦게 도착하면 늦게 일어난 이벤트의 값이 남는다")
    void olderEventArrivingLateIsIgnored() {
        publishInTransaction(eventAt(OCCURRED_AT.plusMinutes(1), List.of(option(11L, 19_000L, false))));

        publishInTransaction(eventAt(OCCURRED_AT, List.of(option(11L, 25_000L, false))));

        assertThat(stored()).extracting(ProductSearchDocument::getMinPrice, ProductSearchDocument::getEventOccurredAt)
            .containsExactly(19_000L, OCCURRED_AT.plusMinutes(1));
    }

    @Test
    @DisplayName("검색용 표 갱신이 실패해도 발행한 트랜잭션은 성공하고, 검색용 표 행은 이전 값 그대로다")
    void failedRefreshKeepsPreviousRow() {
        publishInTransaction(eventAt(OCCURRED_AT, List.of(option(11L, 25_000L, false))));
        ProductSearchDocument before = stored();
        // 옵션이 없는 이벤트는 표시 가격을 정할 수 없어 검색용 표를 만들지 못한다
        ProductChangedEvent withoutOptions = eventAt(OCCURRED_AT.plusMinutes(1), List.of());

        assertThatCode(() -> publishInTransaction(withoutOptions)).doesNotThrowAnyException();

        assertThat(stored()).usingRecursiveComparison().isEqualTo(before);
    }

    @RepeatedTest(20)
    @DisplayName("같은 상품의 상품 정보 변경됨 두 개를 동시에 받아도 늦게 일어난 이벤트의 값이 남는다")
    void concurrentEventsKeepNewerEvent() throws Exception {
        ProductChangedEvent older = eventAt(OCCURRED_AT, List.of(option(11L, 25_000L, false)));
        ProductChangedEvent newer = eventAt(OCCURRED_AT.plusMinutes(1), List.of(option(11L, 19_000L, false)));
        // 두 발행이 같은 순간에 시작하도록 맞춰, 두 리스너가 함께 검색용 표를 고치게 한다
        CyclicBarrier ready = new CyclicBarrier(2);
        List<Future<Void>> results;

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            results = executor.invokeAll(List.of(publishAfter(ready, older), publishAfter(ready, newer)));
        }

        // 리스너의 예외는 커밋 뒤라 로그로만 남아 여기서 잡히지 않는다. 그래서 발행이 끝났는지와 남은 값만 본다
        for (Future<Void> result : results) {
            assertThat(result).succeedsWithin(Duration.ofSeconds(10));
        }
        assertThat(stored()).extracting(ProductSearchDocument::getMinPrice, ProductSearchDocument::getEventOccurredAt)
            .containsExactly(19_000L, OCCURRED_AT.plusMinutes(1));
    }

    private Callable<Void> publishAfter(CyclicBarrier ready, ProductChangedEvent event) {
        return () -> {
            ready.await(10, TimeUnit.SECONDS);
            publishInTransaction(event);
            return null;
        };
    }

    // 판매중인 상품 A의 상품 정보 변경됨이다. 상품 정보 값은 주요소재(목록)와 혼용률(직접 입력)이다
    private static ProductChangedEvent eventAt(LocalDateTime occurredAt, List<ProductChangedEvent.Option> options) {
        return new ProductChangedEvent(UUID.randomUUID(), occurredAt, new ProductChangedEvent.Data(
            PRODUCT_ID, SELLER_ID, NAME, CATEGORY_ID, ProductStatus.ON_SALE, THUMBNAIL_URL, PRODUCT_CREATED_AT, true,
            options, List.of(entry("MAIN_MATERIAL", 20L, "면"), entry("FABRIC_COMPOSITION", null, "면 100%"))));
    }

    private static ProductChangedEvent.Option option(
        Long optionId, long price, boolean soldOut, ProductChangedEvent.AttributeEntry... values) {
        return new ProductChangedEvent.Option(optionId, "옵션 " + optionId, null, price, null, null, soldOut,
            OptionSaleStatus.ON_SALE, List.of(values));
    }

    private static ProductChangedEvent.AttributeEntry entry(String code, Long valueId, String value) {
        return new ProductChangedEvent.AttributeEntry(code, valueId, value);
    }

    private void publishInTransaction(ProductChangedEvent event) {
        transaction().executeWithoutResult(status -> eventPublisher.publish(event));
    }

    private ProductSearchDocument stored() {
        return transaction().execute(status -> entityManager.find(ProductSearchDocument.class, PRODUCT_ID));
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }
}
