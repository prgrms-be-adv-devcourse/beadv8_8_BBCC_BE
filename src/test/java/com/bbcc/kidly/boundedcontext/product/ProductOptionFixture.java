package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;

import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeRole;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import java.util.List;

final class ProductOptionFixture {

    static final long OTHER_SELLER_ID = 3L;
    static final long CLOTHING_PRICE = 25_000L;

    private ProductOptionFixture() {
    }

    /**
     * 옵션을 붙일 의류 유형과 그 유형이 쓰는 값이다. 색상은 필수 옵션 항목(모든 값 허용)이고, 사이즈는 필수 옵션 항목이자
     * 사이즈 역할이며 110 · 120 · FREE만 고를 수 있다. 105는 사이즈의 목록 값이지만 허용되지 않는다. 주요소재는 필수 정보 항목이다.
     */
    record ClothingOptions(
        ProductType type,
        Attribute mainMaterial,
        AttributeValue cotton,
        AttributeValue navy,
        AttributeValue oatmeal,
        AttributeValue size110,
        AttributeValue size120,
        AttributeValue sizeFree,
        AttributeValue size105) {
    }

    static ClothingOptions clothingOptions() {
        Attribute color = ProductTypeFixture.color();
        Attribute size = ProductTypeFixture.size();
        Attribute mainMaterial = ProductTypeFixture.mainMaterial();
        AttributeValue cotton = AttributeValue.create(mainMaterial, "면", null);
        AttributeValue navy = AttributeValue.create(color, "네이비", "#2f3e5c");
        AttributeValue oatmeal = AttributeValue.create(color, "오트밀", "#e3d9c6");
        AttributeValue size110 = AttributeValue.create(size, "110", null);
        AttributeValue size120 = AttributeValue.create(size, "120", null);
        AttributeValue sizeFree = AttributeValue.create(size, "FREE", null);
        AttributeValue size105 = AttributeValue.create(size, "105", null);
        ProductType type = ProductType.define("의류", List.of(
            ProductTypeFixture.requiredOption(color),
            new ProductType.LinkInfo(
                size, AttributeRole.OPTION, true, false, true, false, List.of(size110, size120, sizeFree)),
            ProductTypeFixture.requiredInfo(mainMaterial)));
        return new ClothingOptions(type, mainMaterial, cotton, navy, oatmeal, size110, size120, sizeFree, size105);
    }

    // 기획서 7.1 상품 A를 판매대기로 등록한 상태다. 옵션은 아직 없다
    static Product productA(ClothingOptions clothing) {
        return Product.register(ProductFixture.productAWith(List.of(
            new Product.InfoValueInfo(clothing.mainMaterial(), List.of(clothing.cotton()), null))), clothing.type());
    }

    static Product.OptionInfo option(AttributeValue... values) {
        return new Product.OptionInfo(List.of(values), null);
    }

    // 의류는 가격 구분 항목이 없어서, 가격 구분 값이 빈 묶음 하나에 가격 하나를 보낸다
    static List<Product.PriceInfo> clothingPrice(long price) {
        return List.of(new Product.PriceInfo(List.of(), price));
    }

    // 상품 A의 판매자가 의류 가격 25,000원으로 옵션을 추가하는 요청이다
    static Product.AddOptionsInfo addOptions(List<Product.OptionInfo> options) {
        return new Product.AddOptionsInfo(SELLER_ID, options, clothingPrice(CLOTHING_PRICE));
    }
}
