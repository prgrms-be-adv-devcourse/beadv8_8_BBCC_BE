package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.addOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.clothingOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.clothingPrice;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.option;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.productA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.ClothingOptions;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOptionStatus;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @Test
    @DisplayName("PRICE-01 옵션이 속한 가격 구분 묶음에 가격이 없으면 거절한다")
    void rejectOptionWithoutPrice() {
        ClothingOptions clothing = clothingOptions();
        Product product = productA(clothing);
        Product.AddOptionsInfo info = new Product.AddOptionsInfo(
            SELLER_ID, List.of(option(clothing.navy(), clothing.size110())), List.of());

        assertThatThrownBy(() -> product.addOptions(info, clothing.type()))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.PRICE_GROUP_MISMATCH));
        assertThat(product.getOptions()).isEmpty();
    }

    @ParameterizedTest(name = "{0}원")
    @ValueSource(longs = {0, -1})
    @DisplayName("PRICE-02 판매가가 0 이하이면 거절한다")
    void rejectNonPositivePrice(long price) {
        ClothingOptions clothing = clothingOptions();
        Product product = productA(clothing);
        Product.AddOptionsInfo info = new Product.AddOptionsInfo(
            SELLER_ID, List.of(option(clothing.navy(), clothing.size110())), clothingPrice(price));

        assertThatThrownBy(() -> product.addOptions(info, clothing.type()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("0보다 커야");
        assertThat(product.getOptions()).isEmpty();
    }

    @Test
    @DisplayName("PRICE-03 같은 가격 구분 묶음의 가격이 두 번 오면 거절한다")
    void rejectTwoPricesForSamePriceGroup() {
        ClothingOptions clothing = clothingOptions();
        Product product = productA(clothing);
        Product.AddOptionsInfo info = new Product.AddOptionsInfo(
            SELLER_ID,
            List.of(option(clothing.navy(), clothing.size110())),
            List.of(new Product.PriceInfo(List.of(), 25_000L), new Product.PriceInfo(List.of(), 30_000L)));

        assertThatThrownBy(() -> product.addOptions(info, clothing.type()))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.PRICE_GROUP_MISMATCH));
    }
}
