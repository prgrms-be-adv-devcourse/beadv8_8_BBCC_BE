package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.addOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.clothingOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.option;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.ClothingOptions;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 상품 리포지토리와 상품 애그리거트의 저장 동작을 실제 PostgreSQL로 확인한다. 테스트에 @Transactional을 붙이지 않는다.
 * 항목 · 목록 값 · 상품 유형 · 카테고리 · 실측 항목은 리포지토리가 아직 없어 EntityManager로 저장한다.
 */
@IntegrationTest
class ProductRepositoryIntegrationTest {

    private final ClothingOptions clothing = clothingOptions();

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("옵션만 추가하고 저장해도 상품의 version이 올라, 같은 상품을 동시에 고치는 요청 하나를 막는다")
    void addingOptionsIncreasesVersion() {
        Long productId = savedProductA();
        long before = version(productId);

        inTransaction(() -> productRepository.findById(productId).orElseThrow().addOptions(
            addOptions(List.of(option(managed(clothing.navy()), managed(clothing.size110())))), managedType()));

        assertThat(version(productId)).isGreaterThan(before);
    }

    // 옵션 없는 상품 A를 저장하고 ID를 돌려준다. 항목 · 목록 값 · 유형 · 카테고리 · 실측 항목은 cascade되지 않아 먼저 저장한다
    private Long savedProductA() {
        Product.RegisterInfo info = ProductFixture.productAWith(clothing.type(), List.of(
            new Product.InfoValueInfo(clothing.mainMaterial(), List.of(clothing.cotton()), null)));
        Product product = Product.register(info);
        inTransaction(() -> {
            saveClothingType();
            saveCategory(info.category());
            productRepository.save(product);
        });
        return product.getId();
    }

    private void saveClothingType() {
        Stream.of(clothing.navy(), clothing.size110(), clothing.stripe(), clothing.cotton())
            .map(AttributeValue::getAttribute)
            .forEach(entityManager::persist);
        Stream.of(clothing.cotton(), clothing.navy(), clothing.oatmeal(), clothing.size110(), clothing.size120(),
                clothing.sizeFree(), clothing.size105(), clothing.stripe())
            .forEach(entityManager::persist);
        entityManager.persist(clothing.type());
    }

    private void saveCategory(Category category) {
        entityManager.persist(category.getParent());
        category.getMeasurementItems().forEach(link -> entityManager.persist(link.getMeasurementItem()));
        entityManager.persist(category);
    }

    // 상품 유형과 목록 값은 객체 동일성으로 비교하므로, 상품을 읽은 트랜잭션에서 다시 읽은 객체를 넘긴다
    private ProductType managedType() {
        return entityManager.find(ProductType.class, clothing.type().getId());
    }

    private AttributeValue managed(AttributeValue value) {
        return entityManager.find(AttributeValue.class, value.getId());
    }

    private long version(Long productId) {
        return jdbcTemplate.queryForObject("select version from product.product where id = ?", Long.class, productId);
    }

    private void inTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }
}
