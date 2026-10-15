package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductSearchDocumentFixture.CATEGORY_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductSearchDocumentFixture.NAME;
import static com.bbcc.kidly.boundedcontext.product.ProductSearchDocumentFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.product.ProductSearchDocumentFixture.PRODUCT_CREATED_AT;
import static com.bbcc.kidly.boundedcontext.product.ProductSearchDocumentFixture.PRODUCT_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductSearchDocumentFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductSearchDocumentFixture.THUMBNAIL_URL;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.domain.search.ProductSearchDocument;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
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
            .containsExactly(NAME, THUMBNAIL_URL, SELLER_ID, CATEGORY_ID, ProductStatus.ON_SALE, false,
                PRODUCT_CREATED_AT, OCCURRED_AT, 25_000L, false);
        assertThat(stored.getAttributes())
            .isEqualTo(Map.of("COLOR", List.of(5L), "SIZE", List.of(12L, 13L), "MAIN_MATERIAL", List.of(20L)));
    }

    // 판매중인 상품 A의 상품 정보 변경됨이다. 상품 정보 값은 주요소재(목록)와 혼용률(직접 입력)이다
    private static ProductChangedEvent eventAt(LocalDateTime occurredAt, List<ProductChangedEvent.Option> options) {
        return new ProductChangedEvent(UUID.randomUUID(), occurredAt, new ProductChangedEvent.Data(
            PRODUCT_ID, SELLER_ID, NAME, CATEGORY_ID, ProductStatus.ON_SALE, THUMBNAIL_URL, PRODUCT_CREATED_AT, false,
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
