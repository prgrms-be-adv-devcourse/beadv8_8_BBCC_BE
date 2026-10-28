package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.product.ClothingTopSaver.SavedClothingTop;
import com.bbcc.kidly.boundedcontext.product.app.product.SellerProductService;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.SellerProductDetailResult;
import com.bbcc.kidly.boundedcontext.product.app.type.AttributeService;
import com.bbcc.kidly.boundedcontext.product.app.type.CategoryService;
import com.bbcc.kidly.boundedcontext.product.app.type.MeasurementItemService;
import com.bbcc.kidly.boundedcontext.product.app.type.ProductTypeService;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.AttributeResult;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.CategoryCreateRequest;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.CategoryResult;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.MeasurementItemResult;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeInputType;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeRepository;
import com.bbcc.kidly.global.entity.BaseSoftDeleteEntity;
import com.bbcc.kidly.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 기준 데이터를 지울 때 쓰는 조회와 지운 행의 저장 동작을 실제 PostgreSQL로 확인한다. 테스트에 @Transactional을 붙이지 않는다.
 * 의류 유형 · 항목 · 의류 > 상의는 {@link ClothingTopSaver}가 저장한다.
 */
@IntegrationTest
class ReferenceDataDeleteIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 18, 10, 0);

    @Autowired
    private ClothingTopSaver clothingTopSaver;

    @Autowired
    private ProductTypeRepository productTypeRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductOptionSaver productOptionSaver;

    @Autowired
    private SellerProductService sellerProductService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private AttributeService attributeService;

    @Autowired
    private ProductTypeService productTypeService;

    @Autowired
    private MeasurementItemService measurementItemService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * 「쓰이는 중인지」 찾는 조회와, 그 조회가 true가 되게 하는 쪽(가리키는 유형 · 카테고리)이다.
     */
    record InUse(
        BiFunction<ReferenceDataDeleteIntegrationTest, SavedClothingTop, Boolean> query,
        Function<SavedClothingTop, BaseSoftDeleteEntity> owner) {
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("inUseQueries")
    @DisplayName("TYPE-10 가리키는 유형 · 카테고리를 지우지 않았을 때만 쓰이는 중으로 찾는다")
    void findInUseOnlyWhileOwnerIsLive(InUse inUse) {
        SavedClothingTop saved = clothingTopSaver.save();

        inTransaction(() -> assertThat(inUse.query().apply(this, saved)).isTrue());
        inTransaction(() -> managed(inUse.owner().apply(saved)).delete(NOW));
        inTransaction(() -> assertThat(inUse.query().apply(this, saved)).isFalse());
    }

    static Stream<Named<InUse>> inUseQueries() {
        return Stream.of(
            Named.of("유형에 연결된 항목", new InUse(
                (test, saved) -> test.productTypeRepository.existsByAttributesAttributeAndDeletedAtIsNull(
                    saved.clothing().navy().getAttribute()),
                saved -> saved.clothing().type())),
            Named.of("카테고리가 가리키는 상품 유형", new InUse(
                (test, saved) -> test.categoryRepository.existsByProductTypeAndDeletedAtIsNull(
                    saved.clothing().type()),
                SavedClothingTop::top)),
            Named.of("자식이 있는 중간 카테고리", new InUse(
                (test, saved) -> test.categoryRepository.existsByParentAndDeletedAtIsNull(saved.clothingCategory()),
                SavedClothingTop::top)),
            Named.of("카테고리가 필수로 연결한 실측 항목", new InUse(
                (test, saved) -> test.categoryRepository.existsByMeasurementItemsMeasurementItemAndDeletedAtIsNull(
                    saved.top().getMeasurementItems().get(0).getMeasurementItem()),
                SavedClothingTop::top)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("recreations")
    @DisplayName("TYPE-11 지운 목록 값 · 카테고리 · 상품 유형과 같은 이름은 다시 만들 수 있다")
    void recreateWithNameOfDeleted(Function<SavedClothingTop, BaseSoftDeleteEntity> deleted,
        Function<SavedClothingTop, Object> sameName) {
        SavedClothingTop saved = clothingTopSaver.save();
        inTransaction(() -> managed(deleted.apply(saved)).delete(NOW));

        assertThatCode(() -> inTransaction(() -> entityManager.persist(sameName.apply(saved))))
            .doesNotThrowAnyException();
    }

    static Stream<Arguments> recreations() {
        return Stream.of(
            Arguments.of(Named.of("목록 값 네이비", (Function<SavedClothingTop, BaseSoftDeleteEntity>)
                    saved -> saved.clothing().navy()),
                (Function<SavedClothingTop, Object>) saved -> AttributeValue.create(
                    saved.clothing().navy().getAttribute(), "네이비", "#2f3e5c")),
            Arguments.of(Named.of("의류 아래 상의", (Function<SavedClothingTop, BaseSoftDeleteEntity>)
                    SavedClothingTop::top),
                (Function<SavedClothingTop, Object>) saved -> Category.createMiddle("상의", saved.clothingCategory())),
            Arguments.of(Named.of("상품 유형 의류", (Function<SavedClothingTop, BaseSoftDeleteEntity>)
                    saved -> saved.clothing().type()),
                (Function<SavedClothingTop, Object>) saved -> ProductType.define("의류",
                    List.of(ProductTypeFixture.requiredOption(saved.clothing().navy().getAttribute())))));
    }

    @Test
    @DisplayName("TYPE-11 항목 코드는 지운 항목과 같아도 거절한다")
    void rejectCodeOfDeletedAttribute() {
        SavedClothingTop saved = clothingTopSaver.save();
        Attribute stripe = saved.clothing().stripe().getAttribute();
        inTransaction(() -> managed(stripe).delete(NOW));

        assertThatThrownBy(() -> inTransaction(() -> entityManager.persist(
            Attribute.create("무늬", stripe.getCode(), AttributeInputType.LIST))))
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    @DisplayName("TYPE-09 관리자 목록과 카테고리 트리는 지운 유형 · 항목 · 목록 값 · 실측 항목 · 카테고리를 뺀다")
    void listsExcludeDeleted() {
        SavedClothingTop saved = clothingTopSaver.save();
        MeasurementItem totalLength = saved.top().getMeasurementItems().get(0).getMeasurementItem();
        inTransaction(() -> Stream.of(saved.clothing().oatmeal(), saved.clothing().stripe().getAttribute(),
                saved.top(), saved.clothing().type(), totalLength)
            .forEach(entity -> managed(entity).delete(NOW)));

        assertThat(categoryService.findTree()).singleElement()
            .satisfies(clothing -> assertThat(clothing.children()).isEmpty());
        assertThat(categoryService.findAll()).extracting(CategoryResult::name).containsExactly("의류");
        assertThat(productTypeService.findAll()).isEmpty();
        assertThat(measurementItemService.findAll()).extracting(MeasurementItemResult::code)
            .doesNotContain(totalLength.getCode());
        List<AttributeResult> attributes = attributeService.findAll();
        assertThat(attributes).extracting(AttributeResult::code)
            .doesNotContain(saved.clothing().stripe().getAttribute().getCode());
        assertThat(attributes).filteredOn(attribute -> attribute.code().equals("COLOR")).singleElement()
            .satisfies(color -> assertThat(color.values()).extracting(AttributeResult.Value::name)
                .contains("네이비").doesNotContain("오트밀"));
    }

    @Test
    @DisplayName("TYPE-11 지운 최상위 카테고리와 같은 이름의 최상위 카테고리는 다시 만들 수 있다")
    void recreateTopLevelCategoryWithNameOfDeleted() {
        SavedClothingTop saved = clothingTopSaver.save();
        inTransaction(() -> managed(saved.clothingCategory()).delete(NOW));

        assertThatCode(() -> categoryService.create(new CategoryCreateRequest("의류", null, null, List.of())))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("TYPE-09 지운 목록 값 · 카테고리를 이미 쓰는 상품은 판매자 상세에서 그대로 읽힌다")
    void existingProductKeepsDeletedReferenceData() {
        ProductOption navy110 = productOptionSaver.saveNavy110();
        Long productId = inTransaction(() -> entityManager
            .createQuery("select p.id from Product p", Long.class).getSingleResult());
        inTransaction(() -> {
            managed(navy110.getValues().get(0).getAttributeValue()).delete(NOW);
            entityManager.createQuery("select p.category from Product p", Category.class).getSingleResult()
                .delete(NOW);
        });

        SellerProductDetailResult detail =
            sellerProductService.findMyProductDetail(ProductFixture.SELLER_ID, productId);

        assertThat(detail.options()).extracting(SellerProductDetailResult.Option::optionName)
            .containsExactly("색상: 네이비 / 사이즈: 110");
    }

    // 엔티티를 바꾸는 트랜잭션에서 다시 읽은 객체를 돌려준다
    private BaseSoftDeleteEntity managed(BaseSoftDeleteEntity entity) {
        Object id = entityManager.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(entity);
        return entityManager.find(entity.getClass(), id);
    }

    private void inTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }

    private <T> T inTransaction(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }
}
