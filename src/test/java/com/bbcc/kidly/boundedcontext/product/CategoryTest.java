package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.CategoryFixture.chestWidth;
import static com.bbcc.kidly.boundedcontext.product.CategoryFixture.shoulderWidth;
import static com.bbcc.kidly.boundedcontext.product.CategoryFixture.sleeveLength;
import static com.bbcc.kidly.boundedcontext.product.CategoryFixture.totalLength;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryMeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CategoryTest {

    private final ProductType clothingType = ProductType.define("의류", ProductTypeFixture.clothingLinks());
    private final Category clothing = CategoryFixture.clothing();

    @Test
    @DisplayName("TYPE-01 말단 카테고리는 상품 유형 하나와 필수 실측 항목을 받은 순서대로 가진다")
    void createLeafWithTypeAndMeasurementItems() {
        MeasurementItem totalLength = totalLength();
        MeasurementItem shoulderWidth = shoulderWidth();
        MeasurementItem chestWidth = chestWidth();
        MeasurementItem sleeveLength = sleeveLength();

        Category top = Category.createLeaf(new Category.LeafInfo(
            "상의", clothing, clothingType, List.of(totalLength, shoulderWidth, chestWidth, sleeveLength)));

        assertThat(top.getName()).isEqualTo("상의");
        assertThat(top.getParent()).isSameAs(clothing);
        assertThat(top.getProductType()).isSameAs(clothingType);
        assertThat(top.getMeasurementItems())
            .extracting(CategoryMeasurementItem::getMeasurementItem, CategoryMeasurementItem::getSortOrder)
            .containsExactly(
                tuple(totalLength, 1), tuple(shoulderWidth, 2), tuple(chestWidth, 3), tuple(sleeveLength, 4));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("categoriesUnderLeaf")
    @DisplayName("TYPE-01 말단 카테고리 아래에는 카테고리를 만들 수 없다")
    void rejectCategoryUnderLeaf(Function<Category, ThrowingCallable> createUnder) {
        Category top = CategoryFixture.top(clothing, clothingType);

        assertThatThrownBy(createUnder.apply(top))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("말단 카테고리 아래에는");
    }

    static Stream<Named<Function<Category, ThrowingCallable>>> categoriesUnderLeaf() {
        return Stream.of(
            Named.of("중간 카테고리", leaf -> () -> Category.createMiddle("반팔", leaf)),
            Named.of("말단 카테고리", leaf -> () -> Category.createLeaf(
                new Category.LeafInfo("반팔", leaf, leaf.getProductType(), List.of(totalLength())))));
    }

    @Test
    @DisplayName("TYPE-01 말단 카테고리에서는 상품을 등록할 유형으로 그 카테고리의 유형을 꺼낸다")
    void productTypeToRegisterOfLeaf() {
        Category top = CategoryFixture.top(clothing, clothingType);

        assertThat(top.productTypeToRegister()).isSameAs(clothingType);
    }

    @Test
    @DisplayName("TYPE-01 중간 카테고리는 상품 유형을 가리키지 않으므로 상품을 등록할 유형을 꺼내면 거절한다")
    void rejectProductTypeToRegisterOfMiddle() {

        assertThatThrownBy(clothing::productTypeToRegister).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(CategoryErrorCode.NOT_LEAF_CATEGORY));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("creationsWithNull")
    @DisplayName("이름 · 상품 유형 · 실측 항목 목록과 그 안의 실측 항목이 null이면 거절한다")
    void rejectCreationWithNull(ThrowingCallable create) {
        assertThatThrownBy(create)
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("필수입니다");
    }

    static Stream<Named<ThrowingCallable>> creationsWithNull() {
        ProductType type = ProductType.define("의류", ProductTypeFixture.clothingLinks());
        List<MeasurementItem> items = List.of(totalLength());
        return Stream.of(
            Named.of("중간 카테고리 이름", () -> Category.createMiddle(null, null)),
            Named.of("말단 카테고리 요청", () -> Category.createLeaf(null)),
            Named.of("말단 카테고리 이름", () -> Category.createLeaf(new Category.LeafInfo(null, null, type, items))),
            Named.of("상품 유형", () -> Category.createLeaf(new Category.LeafInfo("상의", null, null, items))),
            Named.of("실측 항목 목록", () -> Category.createLeaf(new Category.LeafInfo("상의", null, type, null))),
            Named.of("실측 항목", () -> Category.createLeaf(
                new Category.LeafInfo("상의", null, type, Arrays.asList(totalLength(), null)))));
    }
}
