package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.CATEGORY_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductFixture.clothing;
import static com.bbcc.kidly.boundedcontext.product.ProductFixture.clothingWithCottonOrOtherMaterial;
import static com.bbcc.kidly.boundedcontext.product.ProductFixture.cottonMaterial;
import static com.bbcc.kidly.boundedcontext.product.ProductFixture.productA;
import static com.bbcc.kidly.boundedcontext.product.ProductFixture.productAWith;
import static com.bbcc.kidly.boundedcontext.product.ProductFixture.productAWithRequired;
import static com.bbcc.kidly.boundedcontext.product.ProductFixture.productAWithoutThumbnail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.bbcc.kidly.boundedcontext.product.ProductFixture.Clothing;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product.InfoValueInfo;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductInfoValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ProductTest {

    @Test
    @DisplayName("SALE-01 새 상품은 판매대기 상태로 만들어지고 기본 정보를 담는다")
    void registerAsSalePending() {
        Clothing clothing = clothing();

        Product product = Product.register(productA(clothing), clothing.type());

        assertThat(product.getStatus()).isEqualTo(ProductStatus.SALE_PENDING);
        assertThat(product.getSellerId()).isEqualTo(2L);
        assertThat(product.getCategoryId()).isEqualTo(1L);
        assertThat(product.getName()).isEqualTo("상품 A");
        assertThat(product.getBrand()).isEqualTo("키들리");
        assertThat(product.getDescription()).isEqualTo("상품 설명");
        assertThat(product.getThumbnailUrl()).isEqualTo("https://…/a-thumbnail.jpg");
        assertThat(product.getImageUrls()).containsExactly("https://…/a-1.jpg");
    }

    @Test
    @DisplayName("썸네일 없이도 상품을 등록한다")
    void registerWithoutThumbnail() {
        Clothing clothing = clothing();

        Product product = Product.register(productAWithoutThumbnail(clothing), clothing.type());

        assertThat(product.getThumbnailUrl()).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"", " "})
    @DisplayName("상품명이 비어 있으면 거절한다")
    void rejectProductWithBlankName(String name) {
        Clothing clothing = clothing();
        Product.RegisterInfo info = productAWithRequired(
            SELLER_ID, CATEGORY_ID, name, List.of("https://…/a-1.jpg"), productA(clothing).infoValues());

        assertThatThrownBy(() -> Product.register(info, clothing.type()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("비어 있을 수 없습니다");
    }

    @Test
    @DisplayName("INFO-01 목록 항목에 값을 두 개 넣으면 거절한다")
    void rejectTwoValuesOnListAttribute() {
        Clothing clothing = clothing();
        AttributeValue polyester = AttributeValue.create(clothing.mainMaterial(), "폴리에스테르", null);
        Product.RegisterInfo info = productAWith(clothing, List.of(
            new InfoValueInfo(clothing.mainMaterial(), List.of(clothing.cotton(), polyester), null)));

        assertThatThrownBy(() -> Product.register(info, clothing.type()))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.MULTIPLE_VALUES_NOT_ALLOWED));
    }

    @Test
    @DisplayName("INFO-01 다중 선택 목록 항목은 값을 여러 개 담는다")
    void registerManyValuesOnMultiListAttribute() {
        Clothing clothing = clothing();
        Product.RegisterInfo info = productAWith(clothing, List.of(
            cottonMaterial(clothing),
            new InfoValueInfo(clothing.feature(), List.of(clothing.brushed(), clothing.stretch()), null)));

        Product product = Product.register(info, clothing.type());

        assertThat(product.getInfoValues())
            .extracting(ProductInfoValue::getAttribute, ProductInfoValue::getAttributeValue)
            .containsExactly(
                tuple(clothing.mainMaterial(), clothing.cotton()),
                tuple(clothing.feature(), clothing.brushed()),
                tuple(clothing.feature(), clothing.stretch()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("duplicatedInfoValues")
    @DisplayName("INFO-01 같은 값이나 같은 항목을 두 번 넣으면 거절한다")
    void rejectDuplicatedInfoValue(Function<Clothing, List<InfoValueInfo>> duplicated) {
        Clothing clothing = clothing();
        List<InfoValueInfo> infoValues = new ArrayList<>(List.of(cottonMaterial(clothing)));
        infoValues.addAll(duplicated.apply(clothing));
        Product.RegisterInfo info = productAWith(clothing, infoValues);

        assertThatThrownBy(() -> Product.register(info, clothing.type()))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.DUPLICATE_INFO_VALUE));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("missingRequiredInfoValues")
    @DisplayName("INFO-02 필수 정보 항목에 값이 없으면 거절한다")
    void rejectMissingRequiredInfoValue(Function<Clothing, List<InfoValueInfo>> infoValues) {
        Clothing clothing = clothing();
        Product.RegisterInfo info = productAWith(clothing, infoValues.apply(clothing));

        assertThatThrownBy(() -> Product.register(info, clothing.type()))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.REQUIRED_INFO_MISSING));
    }

    static Stream<Named<Function<Clothing, List<InfoValueInfo>>>> missingRequiredInfoValues() {
        return Stream.of(
            Named.of("주요소재를 보내지 않음", clothing -> List.of()),
            Named.of("주요소재에 빈 값 목록", clothing -> List.of(
                new InfoValueInfo(clothing.mainMaterial(), List.of(), null))));
    }

    @Test
    @DisplayName("INFO-02 선택 정보 항목은 비워도 등록한다")
    void registerWithEmptyOptionalInfoValues() {
        Clothing clothing = clothing();
        Product.RegisterInfo info = productAWith(clothing, List.of(
            cottonMaterial(clothing),
            new InfoValueInfo(clothing.fabricComposition(), List.of(), null),
            new InfoValueInfo(clothing.feature(), List.of(), null)));

        Product product = Product.register(info, clothing.type());

        assertThat(product.getInfoValues())
            .extracting(ProductInfoValue::getAttribute, ProductInfoValue::getAttributeValue)
            .containsExactly(tuple(clothing.mainMaterial(), clothing.cotton()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("notAllowedMaterials")
    @DisplayName("INFO-03 그 유형에서 고를 수 없는 값을 넣으면 거절한다")
    void rejectNotAllowedInfoValue(Function<Clothing, AttributeValue> material) {
        Clothing clothing = clothingWithCottonOrOtherMaterial();
        Product.RegisterInfo info = productAWith(clothing, List.of(
            new InfoValueInfo(clothing.mainMaterial(), List.of(material.apply(clothing)), null)));

        assertThatThrownBy(() -> Product.register(info, clothing.type()))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.INFO_VALUE_NOT_ALLOWED));
    }

    @Test
    @DisplayName("INFO-03 주요소재 목록에 없는 소재는 기타를 고르고 혼용률에 적어 등록한다")
    void registerOtherMaterialWithFabricComposition() {
        Clothing clothing = clothingWithCottonOrOtherMaterial();
        Product.RegisterInfo info = productAWith(clothing, List.of(
            new InfoValueInfo(clothing.mainMaterial(), List.of(clothing.other()), null),
            new InfoValueInfo(clothing.fabricComposition(), List.of(), "린넨 100%")));

        Product product = Product.register(info, clothing.type());

        assertThat(product.getInfoValues())
            .extracting(
                ProductInfoValue::getAttribute, ProductInfoValue::getAttributeValue, ProductInfoValue::getTextValue)
            .containsExactly(
                tuple(clothing.mainMaterial(), clothing.other(), null),
                tuple(clothing.fabricComposition(), null, "린넨 100%"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("notInfoAttributeValues")
    @DisplayName("그 유형의 정보 항목이 아닌 항목에 값을 넣으면 거절한다")
    void rejectValueOfNotInfoAttribute(Function<Clothing, InfoValueInfo> notInfoValue) {
        Clothing clothing = clothing();
        Product.RegisterInfo info = productAWith(clothing, List.of(
            cottonMaterial(clothing),
            notInfoValue.apply(clothing)));

        assertThatThrownBy(() -> Product.register(info, clothing.type()))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.NOT_INFO_ATTRIBUTE));
    }

    static Stream<Named<Function<Clothing, InfoValueInfo>>> notInfoAttributeValues() {
        return Stream.of(
            Named.of("유형에 없는 항목(신발 사이즈)", clothing -> {
                Attribute shoeSize = ProductTypeFixture.shoeSize();
                return new InfoValueInfo(shoeSize, List.of(AttributeValue.create(shoeSize, "150", null)), null);
            }),
            Named.of("옵션 항목(색상)", clothing -> new InfoValueInfo(
                clothing.color(), List.of(AttributeValue.create(clothing.color(), "네이비", "#2f3e5c")), null)));
    }

    static Stream<Named<Function<Clothing, AttributeValue>>> notAllowedMaterials() {
        return Stream.of(
            Named.of("허용 값에 없는 주요소재 린넨", Clothing::linen),
            Named.of("다른 항목(특징)의 값 기모", Clothing::brushed));
    }

    // 정보 값은 테스트가 만든 유형의 항목 객체로 만들어야 하므로, 의류를 받아 정보 값을 만드는 함수를 넘긴다
    static Stream<Named<Function<Clothing, List<InfoValueInfo>>>> duplicatedInfoValues() {
        return Stream.of(
            Named.of("다중 선택 목록에 같은 값 두 번", clothing -> List.of(
                new InfoValueInfo(clothing.feature(), List.of(clothing.brushed(), clothing.brushed()), null))),
            Named.of("같은 항목 두 번", clothing -> List.of(
                new InfoValueInfo(clothing.feature(), List.of(clothing.brushed()), null),
                new InfoValueInfo(clothing.feature(), List.of(clothing.stretch()), null))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("registrationsWithNull")
    @DisplayName("등록 정보 · 상품 유형 · 판매자 · 카테고리 · 상품명 · 이미지 목록 · 정보 값 목록이 null이면 거절한다")
    void rejectProductWithNull(ThrowingCallable register) {
        assertThatThrownBy(register)
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("필수입니다");
    }

    static Stream<Named<ThrowingCallable>> registrationsWithNull() {
        Clothing clothing = clothing();
        ProductType type = clothing.type();
        List<String> imageUrls = List.of("https://…/a-1.jpg");
        List<InfoValueInfo> infoValues = productA(clothing).infoValues();
        return Stream.of(
            Named.of("등록 정보", () -> Product.register(null, type)),
            Named.of("상품 유형", () -> Product.register(productA(clothing), null)),
            Named.of("판매자", () -> Product.register(
                productAWithRequired(null, CATEGORY_ID, "상품 A", imageUrls, infoValues), type)),
            Named.of("카테고리", () -> Product.register(
                productAWithRequired(SELLER_ID, null, "상품 A", imageUrls, infoValues), type)),
            Named.of("상품명", () -> Product.register(
                productAWithRequired(SELLER_ID, CATEGORY_ID, null, imageUrls, infoValues), type)),
            Named.of("이미지 목록", () -> Product.register(
                productAWithRequired(SELLER_ID, CATEGORY_ID, "상품 A", null, infoValues), type)),
            Named.of("정보 값 목록", () -> Product.register(
                productAWithRequired(SELLER_ID, CATEGORY_ID, "상품 A", imageUrls, null), type)));
    }
}
