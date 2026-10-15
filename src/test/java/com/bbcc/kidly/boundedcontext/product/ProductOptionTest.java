package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.CLOTHING_PRICE;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.OTHER_SELLER_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.addOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.assertRejected;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.clothingOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.clothingPrice;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.option;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.productA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.ClothingOptions;
import com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.Requests;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOptionStatus;
import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ProductOptionTest {

    private final ClothingOptions clothing = clothingOptions();
    private final Product product = productA(clothing);

    @Test
    @DisplayName("SALE-02 새 옵션은 판매중으로 만들어진다")
    void addOptionsOnSale() {

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

        product.addOptions(addOptions(List.of(
            option(clothing.size110(), clothing.navy()),
            option(clothing.navy(), clothing.size120()))), clothing.type());

        assertThat(product.getOptions())
            .extracting(ProductOption::getName)
            .containsExactly("색상: 네이비 / 사이즈: 110", "색상: 네이비 / 사이즈: 120");
    }

    @Test
    @DisplayName("옵션을 추가하면 이미 있던 옵션은 빼고 새로 만든 옵션만 요청 순서대로 돌려준다")
    void returnCreatedOptionsInRequestOrder() {
        product.addOptions(addOptions(List.of(option(clothing.navy(), clothing.size110()))), clothing.type());

        List<ProductOption> created = product.addOptions(addOptions(List.of(
            option(clothing.oatmeal(), clothing.size120()),
            option(clothing.navy(), clothing.size120()))), clothing.type());

        assertThat(created)
            .extracting(ProductOption::getName)
            .containsExactly("색상: 오트밀 / 사이즈: 120", "색상: 네이비 / 사이즈: 120");
    }

    @Test
    @DisplayName("OPT-08 상품을 등록한 판매자가 아니면 옵션을 추가할 수 없다")
    void rejectOptionsFromOtherSeller() {
        Product.AddOptionsInfo info = new Product.AddOptionsInfo(
            OTHER_SELLER_ID, List.of(option(clothing.navy(), clothing.size110())), clothingPrice(CLOTHING_PRICE));

        assertRejected(() -> product.addOptions(info, clothing.type()), ProductErrorCode.NOT_PRODUCT_OWNER);
        assertThat(product.getOptions()).isEmpty();
    }

    @Test
    @DisplayName("OPT-01 필수 옵션 항목에 값이 없는 옵션은 거절한다")
    void rejectOptionWithoutRequiredValue() {

        assertRejected(() -> product.addOptions(addOptions(List.of(
            option(clothing.navy(), clothing.size110()),
            option(clothing.navy()))), clothing.type()), ProductErrorCode.REQUIRED_OPTION_MISSING);
        assertThat(product.getOptions()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("notOptionAttributeValues")
    @DisplayName("OPT-01 그 유형의 옵션 항목이 아닌 항목의 값은 거절한다")
    void rejectValueOfNotOptionAttribute(Function<ClothingOptions, AttributeValue> notOptionValue) {
        AttributeValue value = notOptionValue.apply(clothing);

        assertRejected(() -> product.addOptions(addOptions(List.of(
            option(clothing.navy(), clothing.size110(), value))), clothing.type()),
            ProductErrorCode.NOT_OPTION_ATTRIBUTE);
    }

    @Test
    @DisplayName("OPT-01 한 옵션 항목에 값을 두 개 넣은 옵션은 거절한다")
    void rejectTwoValuesOnOneOptionAttribute() {

        assertRejected(() -> product.addOptions(addOptions(List.of(
            option(clothing.navy(), clothing.oatmeal(), clothing.size110()))), clothing.type()),
            ProductErrorCode.MULTIPLE_VALUES_NOT_ALLOWED);
    }

    @Test
    @DisplayName("OPT-04 · SIZE-01 그 유형에서 고를 수 없는 사이즈 값은 거절한다")
    void rejectNotAllowedOptionValue() {

        assertRejected(() -> product.addOptions(addOptions(List.of(
            option(clothing.navy(), clothing.size105()))), clothing.type()), ProductErrorCode.OPTION_VALUE_NOT_ALLOWED);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("duplicatedOptions")
    @DisplayName("OPT-03 같은 값 묶음의 옵션이 이미 있거나 요청 안에서 겹치면 거절한다")
    void rejectDuplicatedOption(Function<ClothingOptions, Requests> requests) {
        Requests given = requests.apply(clothing);
        if (!given.existing().isEmpty()) {
            product.addOptions(addOptions(given.existing()), clothing.type());
        }

        assertRejected(() -> product.addOptions(addOptions(given.requested()), clothing.type()),
            ProductErrorCode.DUPLICATE_OPTION);
        assertThat(product.getOptions()).hasSameSizeAs(given.existing());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("optionsWithDifferentAttributes")
    @DisplayName("OPT-02 한 상품의 옵션이 서로 다른 옵션 항목 묶음을 쓰면 거절한다")
    void rejectOptionsWithDifferentAttributes(Function<ClothingOptions, Requests> requests) {
        Requests given = requests.apply(clothing);
        if (!given.existing().isEmpty()) {
            product.addOptions(addOptions(given.existing()), clothing.type());
        }

        assertRejected(() -> product.addOptions(addOptions(given.requested()), clothing.type()),
            ProductErrorCode.OPTION_ATTRIBUTES_MISMATCH);
        assertThat(product.getOptions()).hasSameSizeAs(given.existing());
    }

    static Stream<Named<Function<ClothingOptions, Requests>>> optionsWithDifferentAttributes() {
        return Stream.of(
            Named.of("요청 안에서 무늬를 쓴 옵션과 안 쓴 옵션", clothing -> new Requests(
                List.of(),
                List.of(option(clothing.navy(), clothing.size110(), clothing.stripe()),
                    option(clothing.navy(), clothing.size120())))),
            Named.of("무늬 없는 옵션이 있는데 무늬를 쓴 옵션을 추가", clothing -> new Requests(
                List.of(option(clothing.navy(), clothing.size110())),
                List.of(option(clothing.navy(), clothing.size120(), clothing.stripe())))));
    }

    static Stream<Named<Function<ClothingOptions, Requests>>> duplicatedOptions() {
        return Stream.of(
            Named.of("요청 안에서 같은 값 묶음 두 번", clothing -> new Requests(
                List.of(),
                List.of(option(clothing.navy(), clothing.size110()), option(clothing.navy(), clothing.size110())))),
            Named.of("요청 안에서 순서만 바꾼 같은 값 묶음", clothing -> new Requests(
                List.of(),
                List.of(option(clothing.navy(), clothing.size110()), option(clothing.size110(), clothing.navy())))),
            Named.of("이미 있는 옵션과 같은 값 묶음", clothing -> new Requests(
                List.of(option(clothing.navy(), clothing.size110())),
                List.of(option(clothing.size110(), clothing.navy())))));
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
    @DisplayName("옵션 목록이 비어 있으면 거절한다")
    void rejectEmptyOptions() {

        assertThatThrownBy(() -> product.addOptions(addOptions(List.of()), clothing.type()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("비어 있을 수 없습니다");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("additionsWithNull")
    @DisplayName("요청 · 상품 유형 · 판매자 · 옵션 · 가격과 그 안의 값이 null이면 거절한다")
    void rejectAdditionWithNull(ThrowingCallable addOptions) {
        assertThatThrownBy(addOptions)
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("필수입니다");
    }

    static Stream<Named<ThrowingCallable>> additionsWithNull() {
        ClothingOptions clothing = clothingOptions();
        ProductType type = clothing.type();
        List<Product.OptionInfo> options = List.of(option(clothing.navy(), clothing.size110()));
        List<Product.PriceInfo> prices = clothingPrice(CLOTHING_PRICE);
        return Stream.of(
            Named.of("요청", () -> productA(clothing).addOptions(null, type)),
            Named.of("상품 유형", () -> productA(clothing).addOptions(addOptions(options), null)),
            Named.of("판매자", () -> productA(clothing).addOptions(
                new Product.AddOptionsInfo(null, options, prices), type)),
            Named.of("옵션 목록", () -> productA(clothing).addOptions(
                new Product.AddOptionsInfo(SELLER_ID, null, prices), type)),
            Named.of("가격 목록", () -> productA(clothing).addOptions(
                new Product.AddOptionsInfo(SELLER_ID, options, null), type)),
            Named.of("옵션", () -> productA(clothing).addOptions(
                addOptions(Arrays.asList((Product.OptionInfo) null)), type)),
            Named.of("옵션의 값 목록", () -> productA(clothing).addOptions(
                addOptions(List.of(new Product.OptionInfo(null, null))), type)),
            Named.of("옵션의 값", () -> productA(clothing).addOptions(
                addOptions(List.of(new Product.OptionInfo(Arrays.asList(clothing.navy(), null), null))), type)),
            Named.of("가격", () -> productA(clothing).addOptions(
                new Product.AddOptionsInfo(SELLER_ID, options, Arrays.asList((Product.PriceInfo) null)), type)),
            Named.of("가격의 값 목록", () -> productA(clothing).addOptions(new Product.AddOptionsInfo(
                SELLER_ID, options, List.of(new Product.PriceInfo(null, CLOTHING_PRICE))), type)),
            Named.of("판매가", () -> productA(clothing).addOptions(new Product.AddOptionsInfo(
                SELLER_ID, options, List.of(new Product.PriceInfo(List.of(), null))), type)));
    }
}
