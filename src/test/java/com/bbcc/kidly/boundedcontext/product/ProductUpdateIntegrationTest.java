package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.addOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.clothingOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.option;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.addSizeSpecs;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.spec;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.top110Measurements;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.top120Measurements;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.ClothingOptions;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
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
 * 상품 수정으로 바꾼 상품을 저장할 때의 매핑을 실제 PostgreSQL로 확인한다. 테스트에 @Transactional을 붙이지 않고,
 * 저장한 뒤의 상태는 다른 트랜잭션에서 JdbcTemplate으로 읽는다.
 * 상품 A는 판매대기 상의이고 옵션 네이비 · 110과 네이비 · 120, 두 사이즈의 실측, 주요소재 면이 있다.
 */
@IntegrationTest
class ProductUpdateIntegrationTest {

    private final ClothingOptions clothing = clothingOptions();
    private Category category;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("정보 값만 바꿔 저장해도 상품의 version이 오르고, 받지 않은 정보 값 행은 DB에 남지 않는다")
    void changeInfoValuesOnly() {
        Long productId = savedProductA();
        AttributeValue linen = AttributeValue.create(clothing.mainMaterial(), "린넨", null);
        inTransaction(() -> entityManager.persist(linen));
        long before = version(productId);

        inTransaction(() -> {
            Product product = productRepository.findById(productId).orElseThrow();
            Attribute mainMaterial = entityManager.find(Attribute.class, clothing.mainMaterial().getId());
            product.update(sameBasicsWith(product, List.of(
                new Product.InfoValueInfo(mainMaterial, List.of(managed(linen)), null))));
        });

        assertThat(version(productId)).isGreaterThan(before);
        assertThat(jdbcTemplate.queryForList(
            "select attribute_value_id from product.product_info_value where product_id = ?", Long.class, productId))
            .containsExactly(linen.getId());
    }

    // 상품 A의 기본 정보와 카테고리는 그대로 두고 정보 값만 받은 값으로 보내는 수정 값이다
    private Product.UpdateInfo sameBasicsWith(Product product, List<Product.InfoValueInfo> infoValues) {
        return new Product.UpdateInfo(SELLER_ID, product.getVersion(), managedCategory(), product.getName(),
            product.getBrand(), product.getDescription(), product.getThumbnailUrl(), product.getImageUrls(),
            infoValues);
    }

    // 판매대기 상품 A를 옵션 · 실측과 함께 저장하고 ID를 돌려준다.
    // 항목 · 목록 값 · 유형 · 카테고리 · 실측 항목은 cascade되지 않아 먼저 저장한다
    private Long savedProductA() {
        Product.RegisterInfo info = ProductFixture.productAWith(clothing.type(), List.of(
            new Product.InfoValueInfo(clothing.mainMaterial(), List.of(clothing.cotton()), null)));
        Product product = Product.register(info);
        category = info.category();
        product.addOptions(addOptions(List.of(
            option(clothing.navy(), clothing.size110()), option(clothing.navy(), clothing.size120()))), clothing.type());
        product.addSizeSpecs(addSizeSpecs(List.of(
            spec(clothing.size110(), 105, 115, top110Measurements()),
            spec(clothing.size120(), 115, 125, top120Measurements()))), category, clothing.type());
        inTransaction(() -> {
            saveClothingType();
            saveCategory(category);
            productRepository.save(product);
        });
        return product.getId();
    }

    private void saveClothingType() {
        Stream.of(clothing.cotton(), clothing.stripe(), clothing.size110(), clothing.navy())
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

    // 카테고리와 목록 값은 객체 동일성으로 비교하므로, 상품을 읽은 트랜잭션에서 다시 읽은 객체를 넘긴다
    private Category managedCategory() {
        return entityManager.find(Category.class, category.getId());
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
