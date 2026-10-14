package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.addOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.clothingOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.option;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.productA;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.addSizeSpecs;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.spec;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.top110Measurements;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.top120Measurements;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.ClothingOptions;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.SizeSpec;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductSizeSpecTest {

    private final ClothingOptions clothing = clothingOptions();
    private final Product product = productA(clothing);
    private final Category top = CategoryFixture.top(CategoryFixture.clothing(), clothing.type());

    @Test
    @DisplayName("SIZE-03 옵션이 쓰는 사이즈 값마다 실측과 권장 키를 한 행씩 저장한다")
    void addSizeSpecPerSizeValue() {
        product.addOptions(addOptions(List.of(
            option(clothing.navy(), clothing.size110()),
            option(clothing.oatmeal(), clothing.size110()),
            option(clothing.navy(), clothing.size120()))), clothing.type());

        product.addSizeSpecs(addSizeSpecs(List.of(
            spec(clothing.size110(), 105, 115, top110Measurements()),
            spec(clothing.size120(), 115, 125, top120Measurements()))), top, clothing.type());

        assertThat(product.getSizeSpecs())
            .extracting(SizeSpec::getSizeValue, SizeSpec::getMinHeightCm, SizeSpec::getMaxHeightCm,
                SizeSpec::getMeasurements)
            .containsExactly(
                tuple(clothing.size110(), 105, 115, top110Measurements()),
                tuple(clothing.size120(), 115, 125, top120Measurements()));
    }
}
