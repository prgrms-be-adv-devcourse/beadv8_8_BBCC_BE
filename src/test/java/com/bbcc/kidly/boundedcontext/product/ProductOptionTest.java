package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.CLOTHING_PRICE;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.OTHER_SELLER_ID;
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
import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
    @DisplayName("OPT-09 옵션 이름은 값을 보낸 순서와 상관없이 유형의 항목 순서대로 만든다")
    void nameOptionInTypeOrder() {
        ClothingOptions clothing = clothingOptions();
        Product product = productA(clothing);

        product.addOptions(addOptions(List.of(
            option(clothing.size110(), clothing.navy()),
            option(clothing.navy(), clothing.size120()))), clothing.type());

        assertThat(product.getOptions())
            .extracting(ProductOption::getName)
            .containsExactly("색상: 네이비 / 사이즈: 110", "색상: 네이비 / 사이즈: 120");
    }

    @Test
    @DisplayName("OPT-08 상품을 등록한 판매자가 아니면 옵션을 추가할 수 없다")
    void rejectOptionsFromOtherSeller() {
        ClothingOptions clothing = clothingOptions();
        Product product = productA(clothing);
        Product.AddOptionsInfo info = new Product.AddOptionsInfo(
            OTHER_SELLER_ID, List.of(option(clothing.navy(), clothing.size110())), clothingPrice(CLOTHING_PRICE));

        assertThatThrownBy(() -> product.addOptions(info, clothing.type()))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.NOT_PRODUCT_OWNER));
        assertThat(product.getOptions()).isEmpty();
    }

    @Test
    @DisplayName("OPT-01 필수 옵션 항목에 값이 없는 옵션은 거절한다")
    void rejectOptionWithoutRequiredValue() {
        ClothingOptions clothing = clothingOptions();
        Product product = productA(clothing);

        assertThatThrownBy(() -> product.addOptions(addOptions(List.of(
            option(clothing.navy(), clothing.size110()),
            option(clothing.navy()))), clothing.type()))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.REQUIRED_OPTION_MISSING));
        assertThat(product.getOptions()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("notOptionAttributeValues")
    @DisplayName("OPT-01 그 유형의 옵션 항목이 아닌 항목의 값은 거절한다")
    void rejectValueOfNotOptionAttribute(Function<ClothingOptions, AttributeValue> notOptionValue) {
        ClothingOptions clothing = clothingOptions();
        Product product = productA(clothing);
        AttributeValue value = notOptionValue.apply(clothing);

        assertThatThrownBy(() -> product.addOptions(addOptions(List.of(
            option(clothing.navy(), clothing.size110(), value))), clothing.type()))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.NOT_OPTION_ATTRIBUTE));
    }

    @Test
    @DisplayName("OPT-01 한 옵션 항목에 값을 두 개 넣은 옵션은 거절한다")
    void rejectTwoValuesOnOneOptionAttribute() {
        ClothingOptions clothing = clothingOptions();
        Product product = productA(clothing);

        assertThatThrownBy(() -> product.addOptions(addOptions(List.of(
            option(clothing.navy(), clothing.oatmeal(), clothing.size110()))), clothing.type()))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.MULTIPLE_VALUES_NOT_ALLOWED));
    }

    static Stream<Named<Function<ClothingOptions, AttributeValue>>> notOptionAttributeValues() {
        return Stream.of(
            Named.of("정보 항목(주요소재)의 값 면", ClothingOptions::cotton),
            Named.of("유형에 없는 항목(신발 사이즈)의 값 150", clothing -> {
                Attribute shoeSize = ProductTypeFixture.shoeSize();
                return AttributeValue.create(shoeSize, "150", null);
            }));
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
