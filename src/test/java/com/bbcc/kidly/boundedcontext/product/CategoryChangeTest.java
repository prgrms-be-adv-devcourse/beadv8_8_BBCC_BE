package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.CategoryFixture.chestWidth;
import static com.bbcc.kidly.boundedcontext.product.CategoryFixture.shoulderWidth;
import static com.bbcc.kidly.boundedcontext.product.CategoryFixture.sleeveLength;
import static com.bbcc.kidly.boundedcontext.product.CategoryFixture.totalLength;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
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
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

class CategoryChangeTest {

    private static final ProductType CLOTHING_TYPE = ProductType.define("의류", ProductTypeFixture.clothingLinks());
    private static final ProductType GOODS_TYPE = ProductType.define("잡화", ProductTypeFixture.clothingLinks());
    private static final MeasurementItem TOTAL_LENGTH = totalLength();
    private static final MeasurementItem SHOULDER_WIDTH = shoulderWidth();
    private static final MeasurementItem CHEST_WIDTH = chestWidth();
    private static final MeasurementItem SLEEVE_LENGTH = sleeveLength();

    /** 카테고리에 넘길 새 설정이다. */
    record Change(ProductType productType, List<MeasurementItem> measurementItems) {
    }

    // 상의처럼 의류 유형과 총장 · 어깨너비 · 가슴단면 필수 실측을 가진 말단 카테고리
    private static Category top() {
        return Category.createLeaf(new Category.LeafInfo("상의", CategoryFixture.clothing(), CLOTHING_TYPE,
            List.of(TOTAL_LENGTH, SHOULDER_WIDTH, CHEST_WIDTH)));
    }

    @Test
    @DisplayName("상품이 없으면 상품 유형과 필수 실측 항목을 받은 순서대로 바꾼다")
    void changeTypeAndMeasurementItemsWithoutProducts() {
        Category category = top();

        category.change(GOODS_TYPE, List.of(SLEEVE_LENGTH, TOTAL_LENGTH), false);

        assertThat(category.getProductType()).isSameAs(GOODS_TYPE);
        assertThat(category.getMeasurementItems())
            .extracting(CategoryMeasurementItem::getMeasurementItem, CategoryMeasurementItem::getSortOrder)
            .containsExactly(tuple(SLEEVE_LENGTH, 1), tuple(TOTAL_LENGTH, 2));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("changesBreakingProducts")
    @DisplayName("TYPE-07 상품이 있으면 유형 바꾸기 · 필수 실측 더하기는 409로 거절하고 설정을 그대로 둔다")
    void rejectChangeBreakingProducts(Change change) {
        Category category = top();

        assertThatThrownBy(() -> category.change(change.productType(), change.measurementItems(), true))
            .isInstanceOfSatisfying(BusinessException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo(CategoryErrorCode.CHANGE_BREAKS_PRODUCTS);
                assertThat(e.getErrorCode().status()).isEqualTo(HttpStatus.CONFLICT);
            });
        assertThat(category.getProductType()).isSameAs(CLOTHING_TYPE);
        assertThat(category.getMeasurementItems()).extracting(CategoryMeasurementItem::getMeasurementItem)
            .containsExactly(TOTAL_LENGTH, SHOULDER_WIDTH, CHEST_WIDTH);
    }

    static Stream<Named<Change>> changesBreakingProducts() {
        List<MeasurementItem> sameItems = List.of(TOTAL_LENGTH, SHOULDER_WIDTH, CHEST_WIDTH);
        return Stream.of(
            Named.of("유형 바꾸기", new Change(GOODS_TYPE, sameItems)),
            Named.of("필수 실측 더하기",
                new Change(CLOTHING_TYPE, List.of(TOTAL_LENGTH, SHOULDER_WIDTH, CHEST_WIDTH, SLEEVE_LENGTH))),
            Named.of("필수 실측 하나를 다른 것으로 바꾸기",
                new Change(CLOTHING_TYPE, List.of(TOTAL_LENGTH, SHOULDER_WIDTH, SLEEVE_LENGTH))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("changesKeepingProducts")
    @DisplayName("TYPE-07 상품이 있어도 필수 실측 빼기 · 순서 바꾸기는 받는다")
    void acceptChangeKeepingProducts(List<MeasurementItem> measurementItems) {
        Category category = top();

        category.change(CLOTHING_TYPE, measurementItems, true);

        assertThat(category.getMeasurementItems()).extracting(CategoryMeasurementItem::getMeasurementItem)
            .containsExactlyElementsOf(measurementItems);
    }

    static Stream<Named<List<MeasurementItem>>> changesKeepingProducts() {
        return Stream.of(
            Named.of("필수 실측 빼기", List.of(TOTAL_LENGTH, CHEST_WIDTH)),
            Named.of("순서 바꾸기", List.of(CHEST_WIDTH, TOTAL_LENGTH, SHOULDER_WIDTH)));
    }

    @Test
    @DisplayName("TYPE-01 중간 카테고리는 상품 유형과 필수 실측 항목을 바꿀 수 없다")
    void rejectChangingMiddleCategory() {
        Category clothing = CategoryFixture.clothing();

        assertThatThrownBy(() -> clothing.change(GOODS_TYPE, List.of(), false))
            .isInstanceOfSatisfying(BusinessException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo(CategoryErrorCode.MIDDLE_CATEGORY_NOT_CHANGEABLE);
                assertThat(e.getErrorCode().status()).isEqualTo(HttpStatus.BAD_REQUEST);
            });
        assertThat(clothing.getProductType()).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nullArguments")
    @DisplayName("상품 유형 · 필수 실측 목록 · 그 안의 항목이 null이면 거절한다")
    void rejectNullArguments(Change change) {
        Category category = top();

        assertThatNullPointerException()
            .isThrownBy(() -> category.change(change.productType(), change.measurementItems(), false));
    }

    static Stream<Named<Change>> nullArguments() {
        return Stream.of(
            Named.of("상품 유형", new Change(null, List.of(TOTAL_LENGTH))),
            Named.of("필수 실측 목록", new Change(GOODS_TYPE, null)),
            Named.of("필수 실측 항목", new Change(GOODS_TYPE, Arrays.asList(TOTAL_LENGTH, null))));
    }
}
