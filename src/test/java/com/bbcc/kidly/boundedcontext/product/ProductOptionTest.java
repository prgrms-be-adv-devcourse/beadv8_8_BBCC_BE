package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.addOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.clothingOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.option;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.productA;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.ClothingOptions;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOptionStatus;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductOptionTest {

    @Test
    @DisplayName("SALE-02 새 옵션은 판매중으로 만들어진다")
    void addOptionsOnSale() {
        ClothingOptions clothing = clothingOptions();
        Product product = productA(clothing);

        product.addOptions(addOptions(List.of(
            option(clothing.navy(), clothing.size110()),
            option(clothing.navy(), clothing.size120()))), clothing.type());

        assertThat(product.getOptions())
            .extracting(ProductOption::getSaleStatus)
            .containsExactly(ProductOptionStatus.ON_SALE, ProductOptionStatus.ON_SALE);
    }
}
