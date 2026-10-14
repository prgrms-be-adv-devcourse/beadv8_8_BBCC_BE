package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryRepository;
import com.bbcc.kidly.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 카테고리 리포지토리와 카테고리 표의 저장 동작을 실제 PostgreSQL로 확인한다. 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
class CategoryRepositoryIntegrationTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("최상위 카테고리를 이름으로 찾을 때 같은 이름의 하위 카테고리는 찾지 않는다")
    void findRootByNameFindsOnlyRoot() {
        Category clothing = Category.createMiddle("의류", null);
        Category goods = Category.createMiddle("잡화", null);
        Category clothingUnderGoods = Category.createMiddle("의류", goods);
        inTransaction(() -> {
            entityManager.persist(clothing);
            entityManager.persist(goods);
            entityManager.persist(clothingUnderGoods);
        });

        assertThat(categoryRepository.findRootByName("의류")).map(Category::getId).contains(clothing.getId());
        assertThat(categoryRepository.findRootByName("신발")).isEmpty();
    }

    private void inTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }
}
