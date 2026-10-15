package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductListFixture.document;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.app.port.search.ProductSearch;
import com.bbcc.kidly.boundedcontext.product.app.port.search.ProductSearchCondition;
import com.bbcc.kidly.boundedcontext.product.app.port.search.ProductSearchItem;
import com.bbcc.kidly.boundedcontext.product.app.port.search.ProductSearchSort;
import com.bbcc.kidly.boundedcontext.product.domain.search.ProductSearchDocument;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 상품 목록 조회가 검색용 표를 실제 PostgreSQL에서 거르고 정렬하고 자르는지 확인한다. 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
class PostgresProductSearchIntegrationTest {

    @Autowired
    private ProductSearch productSearch;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("SALE-06 판매중지 상품은 목록에 나오지 않고 판매중 상품만 나온다")
    void onlyOnSaleProducts() {
        persist(
            document(1L).build(),
            document(2L).status(ProductStatus.STOPPED).build());

        Page<ProductSearchItem> result = productSearch.search(noCondition(), PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(ProductSearchItem::productId).containsExactly(1L);
    }

    private ProductSearchCondition noCondition() {
        return new ProductSearchCondition(null, null, Map.of(), ProductSearchSort.NEWEST);
    }

    private void persist(ProductSearchDocument... documents) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (ProductSearchDocument document : documents) {
                entityManager.persist(document);
            }
        });
    }
}
