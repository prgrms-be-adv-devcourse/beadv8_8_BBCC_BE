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
import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import java.util.List;
import org.springframework.test.util.ReflectionTestUtils;

final class ProductUpdateFixture {

    static final long VERSION = 3L;
    static final long TOP_ID = 1L;
    static final long BOTTOM_ID = 2L;
    static final long OUTER_ID = 4L;
    static final long SNEAKERS_ID = 5L;

    private ProductUpdateFixture() {
    }

    record ProductA(
        ClothingOptions clothing, Category top, Category outer, Category bottom, Shoes shoes, Product product) {

        Category sneakers() {
            return shoes.sneakers();
        }

        Product.UpdateInfo sameUpdate() {
            return updateWithCategory(top);
        }

        Product.SaleInputsInfo currentSaleInputs() {
            return new Product.SaleInputsInfo(List.of(
                new Product.OptionInfo(List.of(clothing.navy(), clothing.size110()), null),
                new Product.OptionInfo(List.of(clothing.navy(), clothing.size120()), null)),
                clothingPrice(CLOTHING_PRICE), currentSizeSpecs());
        }

        List<Product.SizeSpecInfo> currentSizeSpecs() {
            return List.of(
                spec(clothing.size110(), 105, 115, top110Measurements()),
                spec(clothing.size120(), 115, 125, top120Measurements()));
        }

        Product.UpdateInfo updateWithCategory(Category category) {
            return updateWith(VERSION, category, product.getName());
        }

        Product.UpdateInfo updateWith(long version, Category category, String name) {
            return new Product.UpdateInfo(SELLER_ID, version, category, name, product.getBrand(),
                product.getDescription(), product.getThumbnailUrl(), product.getImageUrls(),
                List.of(new Product.InfoValueInfo(clothing.mainMaterial(), List.of(clothing.cotton()), null)));
        }
    }

    static ProductA salePendingProductA() {
        return productA(false);
    }

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
        Shoes shoes = shoes(clothing);
        ReflectionTestUtils.setField(top, "id", TOP_ID);
        ReflectionTestUtils.setField(bottom, "id", BOTTOM_ID);
        ReflectionTestUtils.setField(outer, "id", OUTER_ID);
        ReflectionTestUtils.setField(shoes.sneakers(), "id", SNEAKERS_ID);

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
            product.startSale(SELLER_ID, top, clothing.type(), ProductFixture.NOW);
        }
        ReflectionTestUtils.setField(product, "version", VERSION);
        return new ProductA(clothing, top, outer, bottom, shoes, product);
    }

    record Shoes(Category sneakers, AttributeValue black, AttributeValue size200) {
    }

    private static Shoes shoes(ClothingOptions clothing) {
        Attribute color = ProductTypeFixture.color();
        Attribute shoeSize = ProductTypeFixture.shoeSize();
        ProductType shoesType = ProductType.define("신발", List.of(
            ProductTypeFixture.requiredOption(color),
            ProductTypeFixture.sizeOption(shoeSize),
            ProductTypeFixture.requiredInfo(clothing.mainMaterial())));
        Category sneakers = Category.createLeaf(new Category.LeafInfo("운동화", Category.createMiddle("신발", null),
            shoesType, List.of(measurement("발길이", "FOOT_LENGTH"))));
        return new Shoes(sneakers, AttributeValue.create(color, "블랙", "#000000"),
            AttributeValue.create(shoeSize, "200", null));
    }

    private static MeasurementItem measurement(String name, String code) {
        return MeasurementItem.create(name, code, "cm");
    }
}
