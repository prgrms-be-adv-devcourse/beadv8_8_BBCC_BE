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
}
