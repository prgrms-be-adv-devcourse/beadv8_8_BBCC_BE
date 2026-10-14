package com.bbcc.kidly.boundedcontext.product;

import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import java.util.List;

final class ProductFixture {

    static final long SELLER_ID = 2L;
    static final long CATEGORY_ID = 1L;

    private ProductFixture() {
    }

    /**
     * 의류 유형과 그 유형이 쓰는 항목 · 목록 값이다. 정보 값은 유형에 연결된 항목 객체로 만들어야 해서 함께 담는다.
     */
    record Clothing(
        ProductType type,
        Attribute color,
        Attribute mainMaterial,
        Attribute fabricComposition,
        AttributeValue cotton) {
    }

    // 색상 · 사이즈는 필수 옵션 항목, 주요소재는 필수 정보 항목(모든 값 허용), 혼용률은 선택 직접 입력 항목이다
    static Clothing clothing() {
        Attribute color = ProductTypeFixture.color();
        Attribute size = ProductTypeFixture.size();
        Attribute mainMaterial = ProductTypeFixture.mainMaterial();
        Attribute fabricComposition = ProductTypeFixture.fabricComposition();
        AttributeValue cotton = AttributeValue.create(mainMaterial, "면", null);
        ProductType type = ProductType.define("의류", List.of(
            ProductTypeFixture.requiredOption(color),
            ProductTypeFixture.sizeOption(size),
            ProductTypeFixture.requiredInfo(mainMaterial),
            ProductTypeFixture.optionalTextInfo(fabricComposition)));
        return new Clothing(type, color, mainMaterial, fabricComposition, cotton);
    }

    // 기획서 7.1 상품 A: 판매자 2, 상의(카테고리 1), 주요소재 면. 혼용률은 비운다
    static Product.RegisterInfo productA(Clothing clothing) {
        return productAWith(clothing, List.of(
            new Product.InfoValueInfo(clothing.mainMaterial(), List.of(clothing.cotton()), null)));
    }

    static Product.RegisterInfo productAWith(Clothing clothing, List<Product.InfoValueInfo> infoValues) {
        return productA(clothing, "https://…/a-thumbnail.jpg", infoValues);
    }

    static Product.RegisterInfo productAWithoutThumbnail(Clothing clothing) {
        Product.RegisterInfo productA = productA(clothing);
        return productA(clothing, null, productA.infoValues());
    }

    private static Product.RegisterInfo productA(
        Clothing clothing, String thumbnailUrl, List<Product.InfoValueInfo> infoValues) {
        return new Product.RegisterInfo(
            SELLER_ID,
            CATEGORY_ID,
            "상품 A",
            "키들리",
            "상품 설명",
            thumbnailUrl,
            List.of("https://…/a-1.jpg"),
            infoValues);
    }

    // 상품 A에서 필수 값만 바꾼 등록 정보다. 필수 값이 비었을 때를 확인하는 테스트가 쓴다
    static Product.RegisterInfo productAWithRequired(
        Long sellerId, Long categoryId, String name, List<String> imageUrls, List<Product.InfoValueInfo> infoValues) {
        return new Product.RegisterInfo(
            sellerId, categoryId, name, "키들리", "상품 설명", "https://…/a-thumbnail.jpg", imageUrls, infoValues);
    }
}
