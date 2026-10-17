package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeRole;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryMeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeAttribute;
import com.bbcc.kidly.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 상품 유형 · 카테고리 설정을 바꿨을 때의 저장 동작을 실제 PostgreSQL로 확인한다. 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
class ReferenceDataChangeIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("유형의 연결을 빼고 순서와 허용 값을 바꿔 저장하면 다시 읽을 때 그대로이고, 뺀 연결 · 허용 값 행은 지워진다")
    void savesChangedLinksAndDeletesRemovedRows() {
        Attribute color = ProductTypeFixture.color();
        Attribute size = ProductTypeFixture.size();
        Attribute material = ProductTypeFixture.mainMaterial();
        Attribute composition = ProductTypeFixture.fabricComposition();
        AttributeValue cotton = AttributeValue.create(material, "면", null);
        AttributeValue other = AttributeValue.create(material, "기타", null);
        ProductType type = ProductType.define("의류", List.of(
            ProductTypeFixture.requiredOption(color), ProductTypeFixture.sizeOption(size),
            restrictedInfo(material, List.of(cotton, other)), ProductTypeFixture.optionalTextInfo(composition)));
        inTransaction(() -> {
            List.of(color, size, material, composition).forEach(entityManager::persist);
            List.of(cotton, other).forEach(entityManager::persist);
            entityManager.persist(type);
        });

        inTransaction(() -> {
            ProductType managed = entityManager.find(ProductType.class, type.getId());
            managed.change(List.of(
                ProductTypeFixture.sizeOption(managed(size)),
                restrictedInfo(managed(material), List.of(managed(cotton))),
                ProductTypeFixture.requiredOption(managed(color))), false);
        });

        inTransaction(() -> {
            ProductType reloaded = entityManager.find(ProductType.class, type.getId());
            assertThat(reloaded.getAttributes())
                .extracting(link -> link.getAttribute().getCode(), ProductTypeAttribute::getSortOrder)
                .containsExactly(tuple("SIZE", 1), tuple("MAIN_MATERIAL", 2), tuple("COLOR", 3));
            assertThat(reloaded.getAllowedValues()).extracting(allowed -> allowed.getAttributeValue().getName())
                .containsExactly("면");
        });
        assertThat(countRows("product.product_type_attribute")).isEqualTo(3);
        assertThat(countRows("product.product_type_allowed_value")).isEqualTo(1);
    }

    @Test
    @DisplayName("카테고리의 필수 실측을 빼고 순서를 바꿔 저장하면 다시 읽을 때 그대로이고, 뺀 실측 연결 행은 지워진다")
    void savesChangedMeasurementItemsAndDeletesRemovedRows() {
        Attribute color = ProductTypeFixture.color();
        ProductType type = ProductType.define("의류", List.of(ProductTypeFixture.requiredOption(color)));
        Category clothing = Category.createMiddle("의류", null);
        List<MeasurementItem> items = List.of(CategoryFixture.totalLength(), CategoryFixture.shoulderWidth(),
            CategoryFixture.chestWidth(), CategoryFixture.sleeveLength());
        Category top = Category.createLeaf(new Category.LeafInfo("상의", clothing, type, items));
        inTransaction(() -> {
            entityManager.persist(color);
            entityManager.persist(type);
            items.forEach(entityManager::persist);
            entityManager.persist(clothing);
            entityManager.persist(top);
        });

        inTransaction(() -> {
            Category managed = entityManager.find(Category.class, top.getId());
            managed.change(managed.getProductType(),
                List.of(managed(items.get(2)), managed(items.get(0))), false);
        });

        inTransaction(() -> {
            Category reloaded = entityManager.find(Category.class, top.getId());
            assertThat(reloaded.getMeasurementItems())
                .extracting(link -> link.getMeasurementItem().getCode(), CategoryMeasurementItem::getSortOrder)
                .containsExactly(tuple("CHEST_WIDTH", 1), tuple("TOTAL_LENGTH", 2));
        });
        assertThat(countRows("product.category_measurement_item")).isEqualTo(2);
    }

    // 모든 값 허용이 아니어서 allowedValues에 넣은 값만 고를 수 있는 필수 정보 항목
    private static ProductType.LinkInfo restrictedInfo(Attribute attribute, List<AttributeValue> allowedValues) {
        return new ProductType.LinkInfo(attribute, AttributeRole.INFO, true, false, false, false, allowedValues);
    }

    // 엔티티는 객체 동일성으로 비교하므로, 바꾸는 트랜잭션에서 다시 읽은 객체를 넘긴다
    @SuppressWarnings("unchecked")
    private <T> T managed(T entity) {
        return (T) entityManager.find(entity.getClass(), entityManager.getEntityManagerFactory()
            .getPersistenceUnitUtil().getIdentifier(entity));
    }

    private Integer countRows(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private void inTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }

    private <T> T inTransaction(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }
}
