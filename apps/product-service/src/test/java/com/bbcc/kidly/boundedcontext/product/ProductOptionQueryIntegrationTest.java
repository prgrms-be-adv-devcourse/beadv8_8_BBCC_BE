package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 재고를 차감할 옵션을 ID 목록으로 읽는 조회를 실제 PostgreSQL로 확인한다. 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
class ProductOptionQueryIntegrationTest {

    private static final long MISSING_OPTION_ID = 999_999L;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductOptionSaver productOptionSaver;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("옵션 ID 목록으로 읽은 옵션은 상품과 함께 읽혀, 트랜잭션 밖에서도 판매 가능 여부를 판단하고 없는 ID는 빠진다")
    void findOptionsWithProduct() {
        ProductOption saved = productOptionSaver.saveNavy110();

        List<ProductOption> found = new TransactionTemplate(transactionManager).execute(
            status -> productRepository.findOptionsWithProduct(List.of(saved.getId(), MISSING_OPTION_ID)));

        assertThat(found).extracting(ProductOption::getId).containsExactly(saved.getId());
        assertThat(found.get(0).isPurchasable()).isFalse();
    }
}
