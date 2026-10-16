package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.clothingLinks;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.color;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.fabricComposition;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.mainMaterial;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.optionalOption;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.optionalTextInfo;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.priceAffectingInfo;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.priceAffectingOption;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.requiredInfo;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.requiredOption;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.restrictedOption;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.shoeSize;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.size;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.sizeInfo;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.sizeOption;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeInputType;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeRole;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeAllowedValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeAttribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeErrorCode;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

class ProductTypeTest {

    @Test
    @DisplayName("TYPE-02 필수 옵션 항목이 있으면 유형을 만든다")
    void defineTypeWithRequiredOptionAttribute() {
        ProductType clothing = ProductType.define("의류", clothingLinks());

        assertThat(clothing.getName()).isEqualTo("의류");
        assertThat(clothing.getAttributes())
            .extracting(link -> link.getAttribute().getCode())
            .containsExactly("COLOR", "SIZE", "MAIN_MATERIAL", "FABRIC_COMPOSITION");
        assertThat(clothing.getAttributes())
            .extracting(ProductTypeAttribute::getRole, ProductTypeAttribute::isRequired,
                ProductTypeAttribute::isAllValuesAllowed, ProductTypeAttribute::getSortOrder,
                ProductTypeAttribute::isSizeRole, ProductTypeAttribute::isPriceAffecting)
            .containsExactly(
                tuple(AttributeRole.OPTION, true, true, 1, false, false),
                tuple(AttributeRole.OPTION, true, true, 2, true, false),
                tuple(AttributeRole.INFO, true, true, 3, false, false),
                tuple(AttributeRole.INFO, false, false, 4, false, false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("linksWithoutRequiredOption")
    @DisplayName("TYPE-02 필수 옵션 항목이 없으면 거절한다")
    void rejectTypeWithoutRequiredOptionAttribute(List<ProductType.LinkInfo> links) {
        assertThatThrownBy(() -> ProductType.define("의류", links))
            .isInstanceOfSatisfying(BusinessException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo(ProductTypeErrorCode.REQUIRED_OPTION_MISSING);
                assertThat(e.getErrorCode().status()).isEqualTo(HttpStatus.BAD_REQUEST);
            });
    }

    static Stream<Named<List<ProductType.LinkInfo>>> linksWithoutRequiredOption() {
        return Stream.of(
            Named.of("연결 없음", List.of()),
            Named.of("정보 항목만", List.of(requiredInfo(mainMaterial()), optionalTextInfo(fabricComposition()))),
            Named.of("옵션 항목이 모두 선택", List.of(optionalOption(color()), optionalOption(size()))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("linksWithAtMostOneSizeRole")
    @DisplayName("TYPE-03 사이즈 역할 항목이 하나 이하면 유형을 만든다")
    void defineTypeWithAtMostOneSizeRoleAttribute(List<ProductType.LinkInfo> links) {
        assertThatCode(() -> ProductType.define("의류", links)).doesNotThrowAnyException();
    }

    static Stream<Named<List<ProductType.LinkInfo>>> linksWithAtMostOneSizeRole() {
        return Stream.of(
            Named.of("사이즈 역할 없음", List.of(requiredOption(color()), requiredOption(size()))),
            Named.of("사이즈 역할 하나", clothingLinks()));
    }

    @Test
    @DisplayName("TYPE-03 사이즈 역할 항목이 둘이면 거절한다")
    void rejectTypeWithTwoSizeRoleAttributes() {
        List<ProductType.LinkInfo> links = List.of(
            requiredOption(color()), sizeOption(size()), sizeOption(shoeSize()));

        assertThatThrownBy(() -> ProductType.define("의류", links))
            .isInstanceOfSatisfying(BusinessException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo(ProductTypeErrorCode.MULTIPLE_SIZE_ROLES);
                assertThat(e.getErrorCode().status()).isEqualTo(HttpStatus.BAD_REQUEST);
            });
    }

    @Test
    @DisplayName("TYPE-03 사이즈 역할을 정보 항목에 두면 거절한다")
    void rejectTypeWithSizeRoleOnInfoAttribute() {
        List<ProductType.LinkInfo> links = List.of(requiredOption(color()), sizeInfo(size()));

        assertThatThrownBy(() -> ProductType.define("의류", links))
            .isInstanceOfSatisfying(BusinessException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo(ProductTypeErrorCode.SIZE_ROLE_ON_INFO);
                assertThat(e.getErrorCode().status()).isEqualTo(HttpStatus.BAD_REQUEST);
            });
    }

    @Test
    @DisplayName("TYPE-04 가격 구분 항목을 옵션 항목에 두면 유형을 만든다")
    void defineTypeWithPriceAffectingOptionAttribute() {
        List<ProductType.LinkInfo> links = List.of(priceAffectingOption(color()), sizeOption(size()));

        ProductType type = ProductType.define("의류", links);

        assertThat(type.getAttributes())
            .extracting(ProductTypeAttribute::isPriceAffecting)
            .containsExactly(true, false);
    }

    @Test
    @DisplayName("TYPE-04 가격 구분 항목을 정보 항목에 두면 거절한다")
    void rejectTypeWithPriceAffectingInfoAttribute() {
        List<ProductType.LinkInfo> links = List.of(
            requiredOption(color()), sizeOption(size()), priceAffectingInfo(mainMaterial()));

        assertThatThrownBy(() -> ProductType.define("의류", links))
            .isInstanceOfSatisfying(BusinessException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo(ProductTypeErrorCode.PRICE_AFFECTING_ON_INFO);
                assertThat(e.getErrorCode().status()).isEqualTo(HttpStatus.BAD_REQUEST);
            });
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = AttributeInputType.class, names = {"MULTI_LIST", "TEXT"})
    @DisplayName("TYPE-08 값을 하나 고르는 항목이 아니면 옵션 항목에 둘 수 없다")
    void rejectTypeWithNonSingleChoiceOptionAttribute(AttributeInputType inputType) {
        Attribute feature = Attribute.create("특징", "FEATURE", inputType);
        List<ProductType.LinkInfo> links = List.of(requiredOption(color()), requiredOption(feature));

        assertThatThrownBy(() -> ProductType.define("의류", links))
            .isInstanceOfSatisfying(BusinessException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo(ProductTypeErrorCode.OPTION_NOT_SINGLE_CHOICE);
                assertThat(e.getErrorCode().status()).isEqualTo(HttpStatus.BAD_REQUEST);
            });
    }

    @Test
    @DisplayName("TYPE-06 모든 값 허용이 아니어도 허용 값이 있으면 유형을 만든다")
    void defineTypeWithAllowedValues() {
        Attribute color = color();
        AttributeValue navy = AttributeValue.create(color, "네이비", "#2f3e5c");

        ProductType clothing = ProductType.define(
            "의류", List.of(restrictedOption(color, List.of(navy)), sizeOption(size())));

        assertThat(clothing.getAllowedValues())
            .extracting(ProductTypeAllowedValue::getAttributeValue)
            .containsExactly(navy);
    }

    @Test
    @DisplayName("TYPE-06 직접 입력 항목은 허용 값 없이도 유형을 만든다")
    void defineTypeWithTextAttributeWithoutAllowedValues() {
        List<ProductType.LinkInfo> links = List.of(requiredOption(color()), optionalTextInfo(fabricComposition()));

        assertThatCode(() -> ProductType.define("의류", links)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("TYPE-06 모든 값 허용이 아닌데 허용 값이 없으면 거절한다")
    void rejectTypeWithoutAllowedValues() {
        List<ProductType.LinkInfo> links = List.of(restrictedOption(color(), List.of()), sizeOption(size()));

        assertThatThrownBy(() -> ProductType.define("의류", links))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("허용 값을 하나 이상");
    }

    @Test
    @DisplayName("TYPE-06 다른 항목의 목록 값을 허용 값으로 넘기면 거절한다")
    void rejectAllowedValueOfOtherAttribute() {
        Attribute size = size();
        AttributeValue size110 = AttributeValue.create(size, "110", null);
        List<ProductType.LinkInfo> links = List.of(restrictedOption(color(), List.of(size110)), sizeOption(size));

        assertThatThrownBy(() -> ProductType.define("의류", links))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("허용 값은 그 항목의 목록 값");
    }

    @Test
    @DisplayName("TYPE-06 모든 값 허용 항목에 허용 값을 넘기면 거절한다")
    void rejectAllowedValuesOnAllValuesAllowedAttribute() {
        Attribute color = color();
        AttributeValue navy = AttributeValue.create(color, "네이비", "#2f3e5c");
        List<ProductType.LinkInfo> links = List.of(
            new ProductType.LinkInfo(color, AttributeRole.OPTION, true, true, false, false, List.of(navy)),
            sizeOption(size()));

        assertThatThrownBy(() -> ProductType.define("의류", links))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("모든 값 허용 항목에는 허용 값을");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"", " "})
    @DisplayName("유형 이름이 비어 있으면 거절한다")
    void rejectTypeWithBlankName(String name) {
        assertThatThrownBy(() -> ProductType.define(name, clothingLinks()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("비어 있을 수 없습니다");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("definitionsWithNull")
    @DisplayName("유형 이름 · 연결 목록 · 연결의 항목 · 역할 · 허용 값 목록 · 허용 값이 null이면 거절한다")
    void rejectTypeWithNull(ThrowingCallable define) {
        assertThatThrownBy(define)
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("필수입니다");
    }

    static Stream<Named<ThrowingCallable>> definitionsWithNull() {
        return Stream.of(
            Named.of("유형 이름", () -> ProductType.define(null, clothingLinks())),
            Named.of("연결 목록", () -> ProductType.define("의류", null)),
            Named.of("연결", () -> ProductType.define("의류", Arrays.asList(requiredOption(color()), null))),
            Named.of("연결의 항목", () -> ProductType.define("의류", List.of(requiredOption(null)))),
            Named.of("연결의 역할", () -> ProductType.define("의류", List.of(
                requiredOption(size()),
                new ProductType.LinkInfo(color(), null, true, true, false, false, List.of())))),
            Named.of("허용 값 목록", () -> ProductType.define("의류", List.of(
                new ProductType.LinkInfo(color(), AttributeRole.OPTION, true, true, false, false, null)))),
            Named.of("허용 값", () -> ProductType.define("의류", List.of(
                restrictedOption(color(), Arrays.asList((AttributeValue) null))))));
    }
}
