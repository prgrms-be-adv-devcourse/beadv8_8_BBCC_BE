package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.CategoryFixture.chestWidth;
import static com.bbcc.kidly.boundedcontext.product.CategoryFixture.shoulderWidth;
import static com.bbcc.kidly.boundedcontext.product.CategoryFixture.sleeveLength;
import static com.bbcc.kidly.boundedcontext.product.CategoryFixture.totalLength;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryMeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CategoryChangeTest {

    private final ProductType clothingType = ProductType.define("의류", ProductTypeFixture.clothingLinks());
    private final ProductType goodsType = ProductType.define("잡화", ProductTypeFixture.clothingLinks());
    private final MeasurementItem totalLength = totalLength();
    private final MeasurementItem shoulderWidth = shoulderWidth();
    private final MeasurementItem chestWidth = chestWidth();
    private final MeasurementItem sleeveLength = sleeveLength();

    // 상의처럼 총장 · 어깨너비 · 가슴단면을 필수 실측으로 가진 말단 카테고리
    private Category top() {
        return Category.createLeaf(new Category.LeafInfo("상의", CategoryFixture.clothing(), clothingType,
            List.of(totalLength, shoulderWidth, chestWidth)));
    }

    @Test
    @DisplayName("상품이 없으면 상품 유형과 필수 실측 항목을 받은 순서대로 바꾼다")
    void changeTypeAndMeasurementItemsWithoutProducts() {
        Category category = top();

        category.change(goodsType, List.of(sleeveLength, totalLength), false);

        assertThat(category.getProductType()).isSameAs(goodsType);
        assertThat(category.getMeasurementItems())
            .extracting(CategoryMeasurementItem::getMeasurementItem, CategoryMeasurementItem::getSortOrder)
            .containsExactly(tuple(sleeveLength, 1), tuple(totalLength, 2));
    }
}
