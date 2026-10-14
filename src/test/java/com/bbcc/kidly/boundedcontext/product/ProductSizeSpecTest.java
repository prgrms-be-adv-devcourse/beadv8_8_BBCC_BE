package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.OTHER_SELLER_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.addOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.assertRejected;
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
import com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.SpecRequests;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.product.SizeSpec;
import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

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

    @ParameterizedTest(name = "{0} ~ {1}")
    @CsvSource({"130, 120", "105, ", ", 115"})
    @DisplayName("SIZE-03 권장 키 범위의 최솟값이 최댓값보다 크거나 한쪽만 있으면 거절한다")
    void rejectInvalidHeightRange(Integer minHeightCm, Integer maxHeightCm) {
        withOptions110And120();

        assertRejected(() -> product.addSizeSpecs(addSizeSpecs(List.of(
            spec(clothing.size110(), minHeightCm, maxHeightCm, top110Measurements()))), top, clothing.type()),
            ProductErrorCode.INVALID_HEIGHT_RANGE);
        assertThat(product.getSizeSpecs()).isEmpty();
    }

    @Test
    @DisplayName("SIZE-05 사이즈 무관 상품의 FREE 행은 권장 키 없이 실측만 저장한다")
    void addFreeSizeSpecWithoutHeightRange() {
        product.addOptions(addOptions(List.of(
            option(clothing.navy(), clothing.sizeFree()),
            option(clothing.oatmeal(), clothing.sizeFree()))), clothing.type());

        product.addSizeSpecs(addSizeSpecs(List.of(
            spec(clothing.sizeFree(), null, null, top110Measurements()))), top, clothing.type());

        assertThat(product.getSizeSpecs())
            .extracting(SizeSpec::getSizeValue, SizeSpec::getMinHeightCm, SizeSpec::getMaxHeightCm)
            .containsExactly(tuple(clothing.sizeFree(), null, null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("duplicatedSizeSpecs")
    @DisplayName("SIZE-03 같은 사이즈 값의 실측이 요청 안에서 겹치거나 이미 있으면 거절한다")
    void rejectDuplicatedSizeSpec(Function<ClothingOptions, SpecRequests> requests) {
        withOptions110And120();
        SpecRequests given = requests.apply(clothing);
        if (!given.existing().isEmpty()) {
            product.addSizeSpecs(addSizeSpecs(given.existing()), top, clothing.type());
        }

        assertRejected(() -> product.addSizeSpecs(addSizeSpecs(given.requested()), top, clothing.type()),
            ProductErrorCode.DUPLICATE_SIZE_SPEC);
        assertThat(product.getSizeSpecs()).hasSameSizeAs(given.existing());
    }

    static Stream<Named<Function<ClothingOptions, SpecRequests>>> duplicatedSizeSpecs() {
        return Stream.of(
            Named.of("요청 안에서 110 두 번", clothing -> new SpecRequests(
                List.of(),
                List.of(spec(clothing.size110(), 105, 115, top110Measurements()),
                    spec(clothing.size110(), 110, 120, top110Measurements())))),
            Named.of("이미 있는 110과 겹침", clothing -> new SpecRequests(
                List.of(spec(clothing.size110(), 105, 115, top110Measurements())),
                List.of(spec(clothing.size110(), 105, 115, top110Measurements())))));
    }

    @Test
    @DisplayName("SIZE-04 상품 카테고리에 없는 실측 항목은 받지 않는다")
    void rejectMeasurementItemNotInCategory() {
        withOptions110And120();
        // 상의의 필수 실측 항목에 하의의 허리단면을 더한다
        Map<String, BigDecimal> withWaist = new HashMap<>(top110Measurements());
        withWaist.put("WAIST_WIDTH", new BigDecimal("25"));

        assertRejected(() -> product.addSizeSpecs(addSizeSpecs(List.of(
            spec(clothing.size110(), 105, 115, withWaist))), top, clothing.type()),
            ProductErrorCode.MEASUREMENT_ITEM_NOT_IN_CATEGORY);
        assertThat(product.getSizeSpecs()).isEmpty();
    }

    @Test
    @DisplayName("SIZE-06 사이즈 역할 항목이 없는 유형의 상품에는 실측을 받지 않는다")
    void rejectSizeSpecForTypeWithoutSizeRole() {
        // 용기처럼 색상만 옵션 항목으로 두고 사이즈 역할 항목이 없는 유형이다
        Attribute color = ProductTypeFixture.color();
        AttributeValue navy = AttributeValue.create(color, "네이비", "#2f3e5c");
        ProductType container = ProductType.define("용기", List.of(ProductTypeFixture.requiredOption(color)));
        Product bottle = Product.register(ProductFixture.productAWith(List.of()), container);
        bottle.addOptions(addOptions(List.of(option(navy))), container);

        assertRejected(() -> bottle.addSizeSpecs(addSizeSpecs(List.of(
            spec(navy, 105, 115, top110Measurements()))), top, container),
            ProductErrorCode.SIZE_SPEC_NOT_ALLOWED);
        assertThat(bottle.getSizeSpecs()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sizeValuesNotInOptions")
    @DisplayName("옵션이 쓰지 않는 사이즈 값의 실측은 거절한다")
    void rejectSizeSpecForSizeValueNotInOptions(Function<ClothingOptions, AttributeValue> sizeValue) {
        product.addOptions(addOptions(List.of(option(clothing.navy(), clothing.size110()))), clothing.type());
        AttributeValue value = sizeValue.apply(clothing);

        assertRejected(() -> product.addSizeSpecs(addSizeSpecs(List.of(
            spec(value, 115, 125, top120Measurements()))), top, clothing.type()),
            ProductErrorCode.SIZE_NOT_IN_OPTIONS);
        assertThat(product.getSizeSpecs()).isEmpty();
    }

    static Stream<Named<Function<ClothingOptions, AttributeValue>>> sizeValuesNotInOptions() {
        return Stream.of(
            Named.of("옵션에 없는 사이즈 120", ClothingOptions::size120),
            Named.of("옵션이 색상으로 쓰는 네이비", ClothingOptions::navy));
    }

    @Test
    @DisplayName("OPT-08 상품을 등록한 판매자가 아니면 실측을 넣을 수 없다")
    void rejectSizeSpecsFromOtherSeller() {
        withOptions110And120();
        Product.AddSizeSpecsInfo info = new Product.AddSizeSpecsInfo(
            OTHER_SELLER_ID, List.of(spec(clothing.size110(), 105, 115, top110Measurements())));

        assertRejected(() -> product.addSizeSpecs(info, top, clothing.type()), ProductErrorCode.NOT_PRODUCT_OWNER);
        assertThat(product.getSizeSpecs()).isEmpty();
    }

    // 상품 A에 네이비 110 · 120 옵션을 붙인다
    private void withOptions110And120() {
        product.addOptions(addOptions(List.of(
            option(clothing.navy(), clothing.size110()),
            option(clothing.navy(), clothing.size120()))), clothing.type());
    }
}
