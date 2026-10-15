package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.CLOTHING_PRICE;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.clothingPrice;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.option;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.spec;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.top110Measurements;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.top120Measurements;

import com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.ClothingOptions;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import java.util.List;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 상품 수정에서 카테고리와 옵션 · 실측을 바꾸는 테스트가 쓰는 상품과 카테고리다.
 * 상품 A는 판매자 2의 상의(카테고리 1)이고 주요소재는 면, 옵션은 네이비 · 110과 네이비 · 120(25,000원)이며
 * 두 사이즈의 상의 실측과 권장 키가 있다. 마지막으로 읽은 version은 3이다.
 */
final class ProductUpdateFixture {

    static final long VERSION = 3L;
    static final long TOP_ID = 1L;
    static final long BOTTOM_ID = 2L;
    static final long OUTER_ID = 4L;
    static final long SNEAKERS_ID = 5L;

    private ProductUpdateFixture() {
    }

    /**
     * 상품 A와 바꿀 카테고리들이다. 아우터는 상의와 필수 실측 항목이 같고, 하의는 총장 · 허리단면 · 엉덩이단면 ·
     * 허벅지단면 · 밑위라 상의 실측으로는 채워지지 않는다. 운동화는 신발 유형이라 상품 A와 유형이 다르다.
     * 신발 유형도 주요소재를 필수 정보 항목으로 써서, 정보 값을 그대로 보내도 정보 값 검사에는 걸리지 않는다.
     */
    record ProductA(
        ClothingOptions clothing, Category top, Category outer, Category bottom, Category sneakers, Product product) {

        // 상품 A에서 카테고리만 받은 값으로 바꾸고 나머지는 지금 값 그대로 보내는 수정 값이다
        Product.UpdateInfo updateWithCategory(Category category) {
            return new Product.UpdateInfo(SELLER_ID, VERSION, category, product.getName(), product.getBrand(),
                product.getDescription(), product.getThumbnailUrl(), product.getImageUrls(),
                List.of(new Product.InfoValueInfo(clothing.mainMaterial(), List.of(clothing.cotton()), null)));
        }
    }

    // 판매대기 상품 A다
    static ProductA salePendingProductA() {
        return productA(false);
    }

    // 판매를 시작한 상품 A다
    static ProductA onSaleProductA() {
        return productA(true);
    }

    private static ProductA productA(boolean startSale) {
        ClothingOptions clothing = ProductOptionFixture.clothingOptions();
        Category clothingCategory = CategoryFixture.clothing();
        Category top = CategoryFixture.top(clothingCategory, clothing.type());
        Category outer = Category.createLeaf(new Category.LeafInfo("아우터", clothingCategory, clothing.type(),
            List.of(CategoryFixture.totalLength(), CategoryFixture.shoulderWidth(), CategoryFixture.chestWidth(),
                CategoryFixture.sleeveLength())));
        Category bottom = Category.createLeaf(new Category.LeafInfo("하의", clothingCategory, clothing.type(),
            List.of(CategoryFixture.totalLength(), measurement("허리단면", "WAIST_WIDTH"),
                measurement("엉덩이단면", "HIP_WIDTH"), measurement("허벅지단면", "THIGH_WIDTH"),
                measurement("밑위", "RISE"))));
        Category sneakers = sneakers(clothing);
        ReflectionTestUtils.setField(top, "id", TOP_ID);
        ReflectionTestUtils.setField(bottom, "id", BOTTOM_ID);
        ReflectionTestUtils.setField(outer, "id", OUTER_ID);
        ReflectionTestUtils.setField(sneakers, "id", SNEAKERS_ID);

        Product product = Product.register(ProductFixture.productAWithRequired(SELLER_ID, top, "상품 A",
            List.of("https://…/a-1.jpg"),
            List.of(new Product.InfoValueInfo(clothing.mainMaterial(), List.of(clothing.cotton()), null))));
        product.addOptions(new Product.AddOptionsInfo(SELLER_ID, List.of(
            option(clothing.navy(), clothing.size110()),
            option(clothing.navy(), clothing.size120())), clothingPrice(CLOTHING_PRICE)), clothing.type());
        product.addSizeSpecs(new Product.AddSizeSpecsInfo(SELLER_ID, List.of(
            spec(clothing.size110(), 105, 115, top110Measurements()),
            spec(clothing.size120(), 115, 125, top120Measurements()))), top, clothing.type());
        if (startSale) {
            product.startSale(SELLER_ID, top, clothing.type());
        }
        ReflectionTestUtils.setField(product, "version", VERSION);
        return new ProductA(clothing, top, outer, bottom, sneakers, product);
    }

    // 신발 > 운동화다. 신발 유형은 색상 · 신발 사이즈가 옵션 항목이고 주요소재(의류와 같은 항목)가 필수 정보 항목이다
    private static Category sneakers(ClothingOptions clothing) {
        ProductType shoes = ProductType.define("신발", List.of(
            ProductTypeFixture.requiredOption(ProductTypeFixture.color()),
            ProductTypeFixture.sizeOption(ProductTypeFixture.shoeSize()),
            ProductTypeFixture.requiredInfo(clothing.mainMaterial())));
        return Category.createLeaf(new Category.LeafInfo("운동화", Category.createMiddle("신발", null), shoes,
            List.of(measurement("발길이", "FOOT_LENGTH"))));
    }

    private static MeasurementItem measurement(String name, String code) {
        return MeasurementItem.create(name, code, "cm");
    }
}
