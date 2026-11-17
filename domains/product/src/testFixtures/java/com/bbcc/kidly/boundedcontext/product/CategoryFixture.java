package com.bbcc.kidly.boundedcontext.product;

import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import java.util.List;

final class CategoryFixture {

    private CategoryFixture() {
    }

    static MeasurementItem totalLength() {
        return MeasurementItem.create("총장", "TOTAL_LENGTH", "cm");
    }

    static MeasurementItem shoulderWidth() {
        return MeasurementItem.create("어깨너비", "SHOULDER_WIDTH", "cm");
    }

    static MeasurementItem chestWidth() {
        return MeasurementItem.create("가슴단면", "CHEST_WIDTH", "cm");
    }

    static MeasurementItem sleeveLength() {
        return MeasurementItem.create("소매길이", "SLEEVE_LENGTH", "cm");
    }

    static MeasurementItem headCircumference() {
        return MeasurementItem.create("머리둘레", "HEAD_CIRCUMFERENCE", "cm");
    }

    static Category clothing() {
        return Category.createMiddle("의류", null);
    }

    static Category top(Category clothing, ProductType clothingType) {
        return Category.createLeaf(new Category.LeafInfo("상의", clothing, clothingType,
            List.of(totalLength(), shoulderWidth(), chestWidth(), sleeveLength())));
    }
}
