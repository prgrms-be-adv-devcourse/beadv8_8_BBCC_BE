package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.addOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.assertRejected;
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
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProductOptionPriceTest {

    private final ClothingOptions clothing = clothingOptions();
    private final Product product = productA(clothing);

    @Test
    @DisplayName("PRICE-03 가격 구분 묶음의 가격을 그 묶음의 옵션 전체에 넣는다")
    void putGroupPriceOnEveryOption() {
        product.addOptions(new Product.AddOptionsInfo(SELLER_ID, List.of(
            option(clothing.navy(), clothing.size110()),
            option(clothing.oatmeal(), clothing.size120())), clothingPrice(27_000L)), clothing.type());

        assertThat(product.getOptions()).extracting(ProductOption::getPrice).containsExactly(27_000L, 27_000L);
    }

    @Test
    @DisplayName("PRICE-03 옵션이 있는 상품에 같은 묶음 가격으로 옵션을 더 추가한다")
    void addMoreOptionsWithSamePrice() {
        product.addOptions(addOptions(List.of(option(clothing.navy(), clothing.size110()))), clothing.type());

        product.addOptions(addOptions(List.of(option(clothing.oatmeal(), clothing.size110()))), clothing.type());

        assertThat(product.getOptions()).hasSize(2);
    }

    @Test
    @DisplayName("PRICE-01 옵션이 속한 가격 구분 묶음에 가격이 없으면 거절한다")
    void rejectOptionWithoutPrice() {
        Product.AddOptionsInfo info = new Product.AddOptionsInfo(
            SELLER_ID, List.of(option(clothing.navy(), clothing.size110())), List.of());

        assertRejected(() -> product.addOptions(info, clothing.type()), ProductErrorCode.PRICE_GROUP_MISMATCH);
        assertThat(product.getOptions()).isEmpty();
    }

    @Test
    @DisplayName("PRICE-03 이미 있는 옵션과 같은 가격 구분 묶음인데 가격이 다르면 거절한다")
    void rejectPriceDifferentFromExistingOptions() {
        product.addOptions(addOptions(List.of(option(clothing.navy(), clothing.size110()))), clothing.type());
        Product.AddOptionsInfo info = new Product.AddOptionsInfo(
            SELLER_ID, List.of(option(clothing.navy(), clothing.size120())), clothingPrice(30_000L));

        assertRejected(
            () -> product.addOptions(info, clothing.type()), ProductErrorCode.EXISTING_OPTION_PRICE_MISMATCH);
        assertThat(product.getOptions()).hasSize(1);
    }

    @ParameterizedTest(name = "{0}원")
    @ValueSource(longs = {0, -1})
    @DisplayName("PRICE-02 판매가가 0 이하이면 거절한다")
    void rejectNonPositivePrice(long price) {
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
        Product.AddOptionsInfo info = new Product.AddOptionsInfo(
            SELLER_ID,
            List.of(option(clothing.navy(), clothing.size110())),
            List.of(new Product.PriceInfo(List.of(), 25_000L), new Product.PriceInfo(List.of(), 30_000L)));

        assertRejected(() -> product.addOptions(info, clothing.type()), ProductErrorCode.PRICE_GROUP_MISMATCH);
    }

    @Test
    @DisplayName("PRICE-01 가격 구분 항목이 없는 유형은 지금 가격 묶음이 빈 값 목록 하나다")
    void currentPricesWithoutPriceAffectingAttribute() {
        product.addOptions(new Product.AddOptionsInfo(SELLER_ID, List.of(
            option(clothing.navy(), clothing.size110()),
            option(clothing.oatmeal(), clothing.size120())), clothingPrice(27_000L)), clothing.type());

        assertThat(product.currentPrices(clothing.type()))
            .containsExactly(new Product.PriceInfo(List.of(), 27_000L));
    }
}
