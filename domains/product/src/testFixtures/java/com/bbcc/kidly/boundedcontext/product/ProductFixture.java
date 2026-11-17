package com.bbcc.kidly.boundedcontext.product;

import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeInputType;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeRole;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import java.time.LocalDateTime;
import java.util.List;

final class ProductFixture {

    static final long SELLER_ID = 2L;
    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 11, 0);

    private static final String NAME = "상품 A";
    private static final String THUMBNAIL_URL = "https://…/a-thumbnail.jpg";
    private static final List<String> IMAGE_URLS = List.of("https://…/a-1.jpg");

    private ProductFixture() {
    }

    record Clothing(
        ProductType type,
        Attribute color,
        Attribute mainMaterial,
        Attribute fabricComposition,
        Attribute feature,
        AttributeValue cotton,
        AttributeValue other,
        AttributeValue linen,
        AttributeValue brushed,
        AttributeValue stretch) {
    }

    static Clothing clothing() {
        return clothing(false, false);
    }

    static Clothing clothingWithCottonOrOtherMaterial() {
        return clothing(true, false);
    }

    static Clothing clothingWithRequiredFabricComposition() {
        return clothing(false, true);
    }

    private static Clothing clothing(boolean materialLimited, boolean fabricCompositionRequired) {
        Attribute color = ProductTypeFixture.color();
        Attribute size = ProductTypeFixture.size();
        Attribute mainMaterial = ProductTypeFixture.mainMaterial();
        Attribute fabricComposition = ProductTypeFixture.fabricComposition();
        Attribute feature = Attribute.create("특징", "FEATURE", AttributeInputType.MULTI_LIST);
        AttributeValue cotton = AttributeValue.create(mainMaterial, "면", null);
        AttributeValue other = AttributeValue.create(mainMaterial, "기타", null);
        AttributeValue linen = AttributeValue.create(mainMaterial, "린넨", null);
        AttributeValue brushed = AttributeValue.create(feature, "기모", null);
        AttributeValue stretch = AttributeValue.create(feature, "신축성", null);
        ProductType.LinkInfo materialLink = ProductTypeFixture.requiredInfo(mainMaterial);
        if (materialLimited) {
            materialLink = new ProductType.LinkInfo(
                mainMaterial, AttributeRole.INFO, true, false, false, false, List.of(cotton, other));
        }
        ProductType.LinkInfo fabricCompositionLink = ProductTypeFixture.optionalTextInfo(fabricComposition);
        if (fabricCompositionRequired) {
            fabricCompositionLink = ProductTypeFixture.requiredInfo(fabricComposition);
        }
        ProductType type = ProductType.define("의류", List.of(
            ProductTypeFixture.requiredOption(color),
            ProductTypeFixture.sizeOption(size),
            materialLink,
            fabricCompositionLink,
            new ProductType.LinkInfo(feature, AttributeRole.INFO, false, true, false, false, List.of())));
        return new Clothing(
            type, color, mainMaterial, fabricComposition, feature, cotton, other, linen, brushed, stretch);
    }

    static Product.RegisterInfo productA(Clothing clothing) {
        return productAWith(clothing.type(), List.of(cottonMaterial(clothing)));
    }

    static Category leafOf(ProductType type) {
        return CategoryFixture.top(CategoryFixture.clothing(), type);
    }

    static Product.InfoValueInfo cottonMaterial(Clothing clothing) {
        return new Product.InfoValueInfo(clothing.mainMaterial(), List.of(clothing.cotton()), null);
    }

    static Product.RegisterInfo productAWith(ProductType type, List<Product.InfoValueInfo> infoValues) {
        return registerInfo(SELLER_ID, leafOf(type), NAME, THUMBNAIL_URL, IMAGE_URLS, infoValues);
    }

    static Product.RegisterInfo productAWithoutThumbnail(Clothing clothing) {
        return registerInfo(
            SELLER_ID, leafOf(clothing.type()), NAME, null, IMAGE_URLS, List.of(cottonMaterial(clothing)));
    }

    static Product.RegisterInfo productAWithRequired(
        Long sellerId, Category category, String name, List<String> imageUrls, List<Product.InfoValueInfo> infoValues) {
        return registerInfo(sellerId, category, name, THUMBNAIL_URL, imageUrls, infoValues);
    }

    private static Product.RegisterInfo registerInfo(
        Long sellerId,
        Category category,
        String name,
        String thumbnailUrl,
        List<String> imageUrls,
        List<Product.InfoValueInfo> infoValues) {
        return new Product.RegisterInfo(
            sellerId, category, name, "키들리", "상품 설명", thumbnailUrl, imageUrls, infoValues);
    }
}
