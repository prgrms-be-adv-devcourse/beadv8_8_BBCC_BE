package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.CATEGORY_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductFixture.clothing;
import static com.bbcc.kidly.boundedcontext.product.ProductFixture.productA;
import static com.bbcc.kidly.boundedcontext.product.ProductFixture.productAWith;
import static com.bbcc.kidly.boundedcontext.product.ProductFixture.productAWithRequired;
import static com.bbcc.kidly.boundedcontext.product.ProductFixture.productAWithoutThumbnail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.product.ProductFixture.Clothing;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product.InfoValueInfo;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import java.util.Collections;
import java.util.List;
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
    @DisplayName("UC-PR01 이미지 목록이 비어 있어도 상품을 등록한다")
    void registerWithoutImages() {
        Clothing clothing = clothing();
        Product.RegisterInfo info = productAWithRequired(
            SELLER_ID, CATEGORY_ID, "상품 A", List.of(), productA(clothing).infoValues());

        Product product = Product.register(info, clothing.type());

        assertThat(product.getImageUrls()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("registrationsWithNull")
    @DisplayName("등록 정보 · 상품 유형 · 판매자 · 카테고리 · 상품명 · 이미지 목록 · 정보 값이 null이면 거절한다")
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
                productAWithRequired(SELLER_ID, CATEGORY_ID, "상품 A", imageUrls, null), type)),
            Named.of("정보 값", () -> Product.register(
                productAWith(Collections.singletonList(null)), type)),
            Named.of("정보 값의 항목", () -> Product.register(
                productAWith(List.of(new InfoValueInfo(null, List.of(clothing.cotton()), null))), type)),
            Named.of("정보 값의 값 목록", () -> Product.register(
                productAWith(List.of(new InfoValueInfo(clothing.mainMaterial(), null, null))), type)),
            Named.of("정보 값의 값 목록 안의 값", () -> Product.register(
                productAWith(List.of(
                    new InfoValueInfo(clothing.mainMaterial(), Collections.singletonList(null), null))), type)));
    }
}
