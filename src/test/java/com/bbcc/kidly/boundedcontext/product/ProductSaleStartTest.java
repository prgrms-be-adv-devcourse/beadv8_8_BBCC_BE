package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.ClothingOptions;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ProductSaleStartTest {

    private final ClothingOptions clothing = clothingOptions();
    private final Product product = productA(clothing);
    private final Category top = CategoryFixture.top(CategoryFixture.clothing(), clothing.type());

    @Test
    @DisplayName("SALE-03 판매 시작 조건을 채운 판매대기 상품은 판매를 시작해 판매중이 된다")
    void startSaleOfReadyProduct() {
        withOptionsAndSizeSpecs110And120();

        boolean started = product.startSale(SELLER_ID, top, clothing.type());

        assertThat(started).isTrue();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("이미 판매중인 상품에 다시 판매를 시작하면 아무것도 바꾸지 않는다 (API 명세 3.7, 재시도 대비)")
    void startSaleAgainChangesNothing() {
        withOptionsAndSizeSpecs110And120();
        product.startSale(SELLER_ID, top, clothing.type());

        boolean started = product.startSale(SELLER_ID, top, clothing.type());

        assertThat(started).isFalse();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("SALE-03 옵션이 없는 상품은 판매를 시작할 수 없다")
    void rejectStartSaleWithoutOptions() {

        assertSaleStartRejected("판매를 시작할 수 없습니다. 옵션이 하나 이상 있어야 합니다.");
    }

    @Test
    @DisplayName("SALE-03 옵션이 쓰는 사이즈 값에 실측이 없으면 판매를 시작할 수 없다")
    void rejectStartSaleWithoutSizeSpec() {
        withOptions110And120();
        product.addSizeSpecs(addSizeSpecs(List.of(
            spec(clothing.size110(), 105, 115, top110Measurements()))), top, clothing.type());

        assertSaleStartRejected("판매를 시작할 수 없습니다. 120 사이즈에 실측이 없습니다.");
    }

    @ParameterizedTest(name = "{0} 빠짐")
    @MethodSource("missingMeasurementItems")
    @DisplayName("SIZE-04 실측 값이 카테고리의 필수 실측 항목을 모두 채우지 못하면 판매를 시작할 수 없다")
    void rejectStartSaleWithMissingMeasurementItem(List<String> removedCodes, String detail) {
        withOptions110And120();
        Map<String, BigDecimal> measurements = new HashMap<>(top110Measurements());
        removedCodes.forEach(measurements::remove);
        product.addSizeSpecs(addSizeSpecs(List.of(
            spec(clothing.size110(), 105, 115, measurements),
            spec(clothing.size120(), 115, 125, top120Measurements()))), top, clothing.type());

        assertSaleStartRejected(detail);
    }

    static Stream<Arguments> missingMeasurementItems() {
        return Stream.of(
            Arguments.of(List.of("SLEEVE_LENGTH"), "판매를 시작할 수 없습니다. 110 사이즈에 소매길이가 없습니다."),
            Arguments.of(List.of("TOTAL_LENGTH"), "판매를 시작할 수 없습니다. 110 사이즈에 총장이 없습니다."),
            Arguments.of(List.of("SLEEVE_LENGTH", "SHOULDER_WIDTH"),
                "판매를 시작할 수 없습니다. 110 사이즈에 어깨너비 · 소매길이가 없습니다."));
    }

    @Test
    @DisplayName("SALE-03 FREE가 아닌 사이즈에 권장 키가 없으면 판매를 시작할 수 없다")
    void rejectStartSaleWithoutHeightRange() {
        withOptions110And120();
        product.addSizeSpecs(addSizeSpecs(List.of(
            spec(clothing.size110(), null, null, top110Measurements()),
            spec(clothing.size120(), 115, 125, top120Measurements()))), top, clothing.type());

        assertSaleStartRejected("판매를 시작할 수 없습니다. 110 사이즈에 권장 키가 없습니다.");
    }

    @Test
    @DisplayName("SIZE-05 사이즈 무관 상품은 FREE 행에 권장 키가 없어도 판매를 시작한다")
    void startSaleOfFreeSizeWithoutHeightRange() {
        withFreeOptions();
        product.addSizeSpecs(addSizeSpecs(List.of(
            spec(clothing.sizeFree(), null, null, top110Measurements()))), top, clothing.type());

        assertThat(product.startSale(SELLER_ID, top, clothing.type())).isTrue();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("SIZE-05 사이즈 무관 상품도 FREE 행에 필수 실측 항목이 빠지면 판매를 시작할 수 없다")
    void rejectStartSaleOfFreeSizeWithMissingMeasurementItem() {
        withFreeOptions();
        Map<String, BigDecimal> measurements = new HashMap<>(top110Measurements());
        measurements.remove("CHEST_WIDTH");
        product.addSizeSpecs(addSizeSpecs(List.of(
            spec(clothing.sizeFree(), null, null, measurements))), top, clothing.type());

        assertSaleStartRejected("판매를 시작할 수 없습니다. FREE 사이즈에 가슴단면이 없습니다.");
    }

    @Test
    @DisplayName("SALE-03 사이즈 역할 항목이 없는 유형의 상품은 실측 없이 판매를 시작한다")
    void startSaleOfTypeWithoutSizeRole() {
        // 용기처럼 색상만 옵션 항목으로 두고 사이즈 역할 항목이 없는 유형이다
        Attribute color = ProductTypeFixture.color();
        AttributeValue navy = AttributeValue.create(color, "네이비", "#2f3e5c");
        ProductType container = ProductType.define("용기", List.of(ProductTypeFixture.requiredOption(color)));
        Product bottle = Product.register(ProductFixture.productAWith(List.of()), container);
        bottle.addOptions(addOptions(List.of(option(navy))), container);

        assertThat(bottle.startSale(SELLER_ID, top, container)).isTrue();
        assertThat(bottle.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("OPT-08 상품을 등록한 판매자가 아니면 판매를 시작할 수 없다")
    void rejectStartSaleFromOtherSeller() {
        withOptionsAndSizeSpecs110And120();

        assertRejected(() -> product.startSale(OTHER_SELLER_ID, top, clothing.type()),
            ProductErrorCode.NOT_PRODUCT_OWNER);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SALE_PENDING);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("incompleteInputs")
    @DisplayName("SALE-03 등록할 때 판매 시작 조건의 입력이 빠지면 빠진 항목을 적어 400으로 거절한다 (10-14 결정)")
    void rejectIncompleteSaleStartInputs(Consumer<ProductSaleStartTest> given, String detail) {
        given.accept(this);

        assertThatThrownBy(() -> product.validateSaleStartInputs(top, clothing.type()))
            .isInstanceOfSatisfying(BusinessException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.SALE_START_INPUT_MISSING);
                assertThat(e.getDetail()).isEqualTo(detail);
            });
    }

    static Stream<Arguments> incompleteInputs() {
        Consumer<ProductSaleStartTest> noOptions = test -> {
        };
        Consumer<ProductSaleStartTest> missingSleeve = test -> {
            test.withOptions110And120();
            Map<String, BigDecimal> measurements = new HashMap<>(top110Measurements());
            measurements.remove("SLEEVE_LENGTH");
            test.product.addSizeSpecs(addSizeSpecs(List.of(
                spec(test.clothing.size110(), 105, 115, measurements),
                spec(test.clothing.size120(), 115, 125, top120Measurements()))), test.top, test.clothing.type());
        };
        return Stream.of(
            Arguments.of(Named.of("옵션 없음", noOptions), "옵션이 하나 이상 있어야 합니다."),
            Arguments.of(Named.of("110 소매길이 빠짐", missingSleeve), "110 사이즈에 소매길이가 없습니다."));
    }

    // 상품 A에 네이비 110 · 120 옵션과, 두 사이즈의 필수 실측 · 권장 키를 모두 넣는다 (상품 규칙 7.1)
    private void withOptionsAndSizeSpecs110And120() {
        withOptions110And120();
        product.addSizeSpecs(addSizeSpecs(List.of(
            spec(clothing.size110(), 105, 115, top110Measurements()),
            spec(clothing.size120(), 115, 125, top120Measurements()))), top, clothing.type());
    }

    // 판매 시작이 409로 거절되고 빠진 항목이 detail에 적히며, 상품은 판매대기로 남는지 확인한다
    private void assertSaleStartRejected(String detail) {
        assertThatThrownBy(() -> product.startSale(SELLER_ID, top, clothing.type()))
            .isInstanceOfSatisfying(BusinessException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.SALE_START_CONDITION_NOT_MET);
                assertThat(e.getDetail()).isEqualTo(detail);
            });
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SALE_PENDING);
    }

    // 상품 A에 네이비 110 · 120 옵션을 붙인다
    private void withOptions110And120() {
        product.addOptions(addOptions(List.of(
            option(clothing.navy(), clothing.size110()),
            option(clothing.navy(), clothing.size120()))), clothing.type());
    }

    // 상품 A에 네이비 · 오트밀 FREE 옵션을 붙인다(사이즈 무관 상품)
    private void withFreeOptions() {
        product.addOptions(addOptions(List.of(
            option(clothing.navy(), clothing.sizeFree()),
            option(clothing.oatmeal(), clothing.sizeFree()))), clothing.type());
    }
}
