package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductSearchDocumentFixture.PRODUCT_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductSearchDocumentFixture.changeInfo;
import static com.bbcc.kidly.boundedcontext.product.ProductSearchDocumentFixture.option;
import static com.bbcc.kidly.boundedcontext.product.ProductSearchDocumentFixture.value;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.domain.search.ProductSearchDocument;
import com.bbcc.kidly.boundedcontext.product.domain.search.ProductSearchDocumentRepository;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }
}
