package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.addOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.clothingOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.option;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.productA;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.addSizeSpecs;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.spec;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.top110Measurements;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.top120Measurements;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.ClothingOptions;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductOptionPurchasableTest {

    private final ClothingOptions clothing = clothingOptions();
    private final Product product = productA(clothing);
    private final Category top = CategoryFixture.top(CategoryFixture.clothing(), clothing.type());

    @Test
    @DisplayName("SALE-04 판매중 상품의 판매중 옵션은 살 수 있다")
    void optionOfProductOnSaleIsPurchasable() {
        withOptionsAndSizeSpecs110And120();
        product.startSale(SELLER_ID, top, clothing.type());

        assertThat(product.getOptions())
            .extracting(ProductOption::isPurchasable)
            .containsExactly(true, true);
    }

    @Test
    @DisplayName("SALE-04 판매를 시작하기 전 상품의 옵션은 살 수 없다 (INV-13)")
    void optionOfProductBeforeSaleIsNotPurchasable() {
        withOptionsAndSizeSpecs110And120();

        assertThat(product.getOptions())
            .extracting(ProductOption::isPurchasable)
            .containsExactly(false, false);
    }

    // 상품 A에 네이비 110 · 120 옵션과, 두 사이즈의 필수 실측 · 권장 키를 모두 넣는다
    private void withOptionsAndSizeSpecs110And120() {
        product.addOptions(addOptions(List.of(
            option(clothing.navy(), clothing.size110()),
            option(clothing.navy(), clothing.size120()))), clothing.type());
        product.addSizeSpecs(addSizeSpecs(List.of(
            spec(clothing.size110(), 105, 115, top110Measurements()),
            spec(clothing.size120(), 115, 125, top120Measurements()))), top, clothing.type());
    }
}
