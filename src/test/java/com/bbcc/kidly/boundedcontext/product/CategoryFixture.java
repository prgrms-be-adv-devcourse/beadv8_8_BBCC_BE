package com.bbcc.kidly.boundedcontext.product;

import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import java.util.List;

final class CategoryFixture {

    private CategoryFixture() {
    }

    static MeasurementItem totalLength() {
        return MeasurementItem.create("TOTAL_LENGTH", "총장", "cm");
    }

    static MeasurementItem shoulderWidth() {
        return MeasurementItem.create("SHOULDER_WIDTH", "어깨너비", "cm");
    }

    static MeasurementItem chestWidth() {
        return MeasurementItem.create("CHEST_WIDTH", "가슴단면", "cm");
    }

    static MeasurementItem sleeveLength() {
        return MeasurementItem.create("SLEEVE_LENGTH", "소매길이", "cm");
    }

    // 기준 데이터의 최상위 중간 카테고리 의류다
    static Category clothing() {
        return Category.createMiddle("의류", null);
    }

    // 기준 데이터의 의류 > 상의다. 의류 유형을 가리키고, 필수 실측 항목은 총장 · 어깨너비 · 가슴단면 · 소매길이 순서다
    static Category top(Category clothing, ProductType clothingType) {
        return Category.createLeaf(new Category.LeafInfo("상의", clothing, clothingType,
            List.of(totalLength(), shoulderWidth(), chestWidth(), sleeveLength())));
    }
}
