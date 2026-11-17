package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class CategoryRepositoryIntegrationTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("최상위 카테고리를 이름으로 찾을 때 같은 이름의 하위 카테고리는 찾지 않는다")
    void findTopLevelByNameFindsOnlyTopLevel() {
        Category clothing = Category.createMiddle("의류", null);
        Category goods = Category.createMiddle("잡화", null);
        Category clothingUnderGoods = Category.createMiddle("의류", goods);
        inTransaction(() -> {
            entityManager.persist(clothing);
            entityManager.persist(goods);
            entityManager.persist(clothingUnderGoods);
        });

        inTransaction(() -> {
            assertThat(categoryRepository.findTopLevelByName("의류")).map(Category::getId).contains(clothing.getId());
            assertThat(categoryRepository.findTopLevelByName("신발")).isEmpty();
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("duplicates")
    @DisplayName("같은 부모 아래 같은 이름의 카테고리는 DB UNIQUE가 거절한다")
    void uniqueConstraintsRejectDuplicates(Duplicate duplicate) {
        // EntityManager로 바로 저장하므로 Spring이 바꾸지 않은 Hibernate의 제약 위반 예외가 나온다
        assertThatThrownBy(() -> inTransaction(() -> duplicate.persist(this)))
            .isInstanceOf(ConstraintViolationException.class);
    }

    static Stream<Named<Duplicate>> duplicates() {
        return Stream.of(
            Named.of("의류 아래 상의 두 번", test -> {
                Category clothing = Category.createMiddle("의류", null);
                test.entityManager.persist(clothing);
                test.entityManager.persist(Category.createMiddle("상의", clothing));
                test.entityManager.persist(Category.createMiddle("상의", clothing));
            }));
    }

    @Test
    @DisplayName("말단 카테고리의 실측 항목 연결은 카테고리를 채워 저장되고, 연결한 순서로 다시 읽힌다")
    void measurementItemLinksAreSavedWithCategoryInOrder() {
        Category top = savedTop();
        // id를 그대로 두는 UPDATE는 색인이 원래 자리를 가리켜, 색인으로 읽을 때 순서가 바뀌지 않는다
        jdbcTemplate.update("with moved as (delete from product.category_measurement_item where id = (select min(id) "
            + "from product.category_measurement_item where category_id = ?) returning *) "
            + "insert into product.category_measurement_item select * from moved", top.getId());

        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from product.category_measurement_item where category_id = ?", Long.class, top.getId()))
            .isEqualTo(4);
        inTransaction(() -> assertThat(categoryRepository.findById(top.getId()).orElseThrow().getMeasurementItems())
            .extracting(link -> link.getMeasurementItem().getCode())
            .containsExactly("TOTAL_LENGTH", "SHOULDER_WIDTH", "CHEST_WIDTH", "SLEEVE_LENGTH"));
    }

    @Test
    @DisplayName("TYPE-01 지연 로딩 프록시로 받은 말단 카테고리 아래에도 카테고리를 만들 수 없다")
    void rejectCategoryUnderLeafProxy() {
        Category top = savedTop();

        inTransaction(() -> {
            Category proxy = entityManager.getReference(Category.class, top.getId());

            assertThatThrownBy(() -> Category.createMiddle("반팔", proxy))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(CategoryErrorCode.PARENT_IS_LEAF);
                    assertThat(e.getErrorCode().status()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
        });
    }

    private Category savedTop() {
        Category clothing = Category.createMiddle("의류", null);
        List<MeasurementItem> items = List.of(CategoryFixture.totalLength(), CategoryFixture.shoulderWidth(),
            CategoryFixture.chestWidth(), CategoryFixture.sleeveLength());
        return inTransaction(() -> {
            for (int i = items.size() - 1; i >= 0; i--) {
                entityManager.persist(items.get(i));
            }
            entityManager.persist(clothing);
            Category top = Category.createLeaf(new Category.LeafInfo("상의", clothing, savedType(), items));
            entityManager.persist(top);
            return top;
        });
    }

    private ProductType savedType() {
        Attribute color = ProductTypeFixture.color();
        ProductType type = ProductType.define("의류", List.of(ProductTypeFixture.requiredOption(color)));
        entityManager.persist(color);
        entityManager.persist(type);
        return type;
    }

    interface Duplicate {
        void persist(CategoryRepositoryIntegrationTest test);
    }

    private void inTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }

    private <T> T inTransaction(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }
}
