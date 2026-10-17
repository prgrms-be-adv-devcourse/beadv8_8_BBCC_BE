package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.color;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.fabricComposition;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.mainMaterial;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.optionalOption;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.optionalTextInfo;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.priceAffectingOption;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.requiredInfo;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.requiredOption;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.restrictedOption;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.restrictedSizeOption;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.size;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.sizeOption;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.groups.Tuple.tuple;

import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeInputType;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeRole;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeAttribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeErrorCode;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

class ProductTypeChangeTest {

    private static final Attribute COLOR = color();
    private static final Attribute SIZE = size();
    private static final Attribute MATERIAL = mainMaterial();
    private static final Attribute COMPOSITION = fabricComposition();
    private static final Attribute PATTERN = Attribute.create("패턴", "PATTERN", AttributeInputType.LIST);
    private static final Attribute SEASON = Attribute.create("계절", "SEASON", AttributeInputType.LIST);
    private static final AttributeValue NAVY = AttributeValue.create(COLOR, "네이비", "#2f3e5c");
    private static final AttributeValue GRAY = AttributeValue.create(COLOR, "그레이", "#9e9e9e");
    private static final AttributeValue WHITE = AttributeValue.create(COLOR, "화이트", "#ffffff");
    private static final AttributeValue SIZE_110 = AttributeValue.create(SIZE, "110", null);

    // 기준 데이터의 의류와 같은 연결: 색상 · 사이즈 필수 옵션, 주요소재 필수 정보, 혼용률 선택 정보
    private static ProductType clothing() {
        return ProductType.define("의류", List.of(
            requiredOption(COLOR), sizeOption(SIZE), requiredInfo(MATERIAL), optionalTextInfo(COMPOSITION)));
    }

    // 상품이 있는 유형으로 쓴다. 색상은 네이비 · 그레이만 허용하고, 나머지는 의류와 같다
    private static ProductType typeInUse() {
        return ProductType.define("의류", List.of(
            restrictedOption(COLOR, List.of(NAVY, GRAY)), sizeOption(SIZE), requiredInfo(MATERIAL),
            optionalTextInfo(COMPOSITION)));
    }

    @Test
    @DisplayName("상품이 없으면 연결을 빼고 역할 · 필수 · 순서를 바꾼 목록으로 통째로 바꾼다")
    void replaceLinksWithoutProducts() {
        ProductType type = clothing();

        type.change(List.of(sizeOption(SIZE), requiredOption(MATERIAL), optionalOption(COLOR)), false);

        assertThat(type.getAttributes())
            .extracting(link -> link.getAttribute().getCode(), ProductTypeAttribute::getRole,
                ProductTypeAttribute::isRequired, ProductTypeAttribute::isSizeRole, ProductTypeAttribute::getSortOrder)
            .containsExactly(
                tuple("SIZE", AttributeRole.OPTION, true, true, 1),
                tuple("MAIN_MATERIAL", AttributeRole.OPTION, true, false, 2),
                tuple("COLOR", AttributeRole.OPTION, false, false, 3));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("changesBreakingProducts")
    @DisplayName("TYPE-07 상품이 있으면 기존 상품을 깨는 변경은 409로 거절하고 연결을 그대로 둔다")
    void rejectChangeBreakingProducts(List<ProductType.LinkInfo> links) {
        ProductType type = typeInUse();

        assertThatThrownBy(() -> type.change(links, true))
            .isInstanceOfSatisfying(BusinessException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo(ProductTypeErrorCode.CHANGE_BREAKS_PRODUCTS);
                assertThat(e.getErrorCode().status()).isEqualTo(HttpStatus.CONFLICT);
            });
        assertThat(type.getAttributes())
            .extracting(link -> link.getAttribute().getCode())
            .containsExactly("COLOR", "SIZE", "MAIN_MATERIAL", "FABRIC_COMPOSITION");
        assertThat(type.getAllowedValues()).extracting(allowed -> allowed.getAttributeValue().getName())
            .containsExactly("네이비", "그레이");
    }

    static Stream<Named<List<ProductType.LinkInfo>>> changesBreakingProducts() {
        ProductType.LinkInfo color = restrictedOption(COLOR, List.of(NAVY, GRAY));
        ProductType.LinkInfo size = sizeOption(SIZE);
        ProductType.LinkInfo material = requiredInfo(MATERIAL);
        ProductType.LinkInfo composition = optionalTextInfo(COMPOSITION);
        return Stream.of(
            Named.of("연결 빼기", List.of(color, size, material)),
            Named.of("정보 항목을 옵션으로", List.of(color, size, requiredOption(MATERIAL), composition)),
            Named.of("사이즈 역할 빼기", List.of(color, requiredOption(SIZE), material, composition)),
            Named.of("가격 구분 더하기", List.of(priceAffectingOption(COLOR), size, material, composition)),
            Named.of("선택 항목을 필수로", List.of(color, size, material,
                new ProductType.LinkInfo(COMPOSITION, AttributeRole.INFO, true, false, false, false, List.of()))),
            Named.of("허용 값 빼기", List.of(restrictedOption(COLOR, List.of(NAVY)), size, material, composition)),
            Named.of("모든 값 허용을 허용 값만으로",
                List.of(color, restrictedSizeOption(SIZE, List.of(SIZE_110)), material, composition)),
            Named.of("옵션 항목 추가", List.of(color, size, material, composition, optionalOption(PATTERN))),
            Named.of("필수 정보 항목 추가", List.of(color, size, material, composition, requiredInfo(SEASON))),
            Named.of("옵션 항목 순서 변경", List.of(size, color, material, composition)));
    }

    @Test
    @DisplayName("상품이 없어도 필수 옵션 항목이 없는 연결 목록이면 거절하고 연결을 그대로 둔다")
    void rejectInvalidLinksWithoutProducts() {
        ProductType type = clothing();

        assertThatThrownBy(() -> type.change(List.of(optionalOption(COLOR), requiredInfo(MATERIAL)), false))
            .isInstanceOfSatisfying(BusinessException.class, e ->
                assertThat(e.getErrorCode()).isEqualTo(ProductTypeErrorCode.REQUIRED_OPTION_MISSING));
        assertThat(type.getAttributes())
            .extracting(link -> link.getAttribute().getCode())
            .containsExactly("COLOR", "SIZE", "MAIN_MATERIAL", "FABRIC_COMPOSITION");
    }

    @Test
    @DisplayName("연결 목록이 null이면 거절한다")
    void rejectNullLinks() {
        ProductType type = clothing();

        assertThatNullPointerException().isThrownBy(() -> type.change(null, false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("changesKeepingProducts")
    @DisplayName("TYPE-07 상품이 있어도 기존 상품을 깨지 않는 변경은 받아 연결을 바꾼다")
    void acceptChangeKeepingProducts(List<ProductType.LinkInfo> links) {
        ProductType type = typeInUse();

        type.change(links, true);

        assertThat(type.getAttributes())
            .extracting(link -> link.getAttribute().getCode(), ProductTypeAttribute::isRequired,
                ProductTypeAttribute::isAllValuesAllowed)
            .containsExactlyElementsOf(links.stream()
                .map(link -> tuple(link.attribute().getCode(), link.required(), link.allValuesAllowed()))
                .toList());
    }

    static Stream<Named<List<ProductType.LinkInfo>>> changesKeepingProducts() {
        ProductType.LinkInfo color = restrictedOption(COLOR, List.of(NAVY, GRAY));
        ProductType.LinkInfo size = sizeOption(SIZE);
        ProductType.LinkInfo material = requiredInfo(MATERIAL);
        ProductType.LinkInfo composition = optionalTextInfo(COMPOSITION);
        return Stream.of(
            Named.of("허용 값 추가",
                List.of(restrictedOption(COLOR, List.of(NAVY, GRAY, WHITE)), size, material, composition)),
            Named.of("허용 값만을 모든 값 허용으로", List.of(requiredOption(COLOR), size, material, composition)),
            Named.of("선택 정보 항목 추가", List.of(color, size, material, composition,
                new ProductType.LinkInfo(SEASON, AttributeRole.INFO, false, true, false, false, List.of()))),
            Named.of("필수 항목을 선택으로", List.of(color, size,
                new ProductType.LinkInfo(MATERIAL, AttributeRole.INFO, false, true, false, false, List.of()),
                composition)),
            Named.of("정보 항목 순서 변경", List.of(color, size, composition, material)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("duplicatedLinks")
    @DisplayName("같은 항목을 두 번 연결하거나 같은 허용 값을 두 번 고르면 상품이 없어도 400으로 거절한다")
    void rejectDuplicatedLinks(List<ProductType.LinkInfo> links, ProductTypeErrorCode errorCode) {
        ProductType type = typeInUse();

        assertThatThrownBy(() -> type.change(links, false))
            .isInstanceOfSatisfying(BusinessException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo(errorCode);
                assertThat(e.getErrorCode().status()).isEqualTo(HttpStatus.BAD_REQUEST);
            });
    }

    static Stream<Arguments> duplicatedLinks() {
        ProductType.LinkInfo size = sizeOption(SIZE);
        ProductType.LinkInfo material = requiredInfo(MATERIAL);
        return Stream.of(
            Arguments.of(Named.of("같은 항목 두 번", List.of(requiredOption(COLOR), size,
                    new ProductType.LinkInfo(SIZE, AttributeRole.INFO, false, true, false, false, List.of()))),
                ProductTypeErrorCode.DUPLICATE_ATTRIBUTE),
            Arguments.of(Named.of("같은 허용 값 두 번",
                    List.of(restrictedOption(COLOR, List.of(NAVY, NAVY)), size, material)),
                ProductTypeErrorCode.DUPLICATE_ALLOWED_VALUE));
    }
}
