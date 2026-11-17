package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.forceSaleStatus;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.addOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.assertRejected;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.clothingOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.clothingPrice;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.option;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.productA;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.ClothingOptions;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOptionStatus;
import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductOptionPriceTest {

    private final ClothingOptions clothing = clothingOptions();
    private final Product product = productA(clothing);

    private final Attribute color = ProductTypeFixture.color();
    private final Attribute size = ProductTypeFixture.size();
    private final AttributeValue navy = AttributeValue.create(color, "네이비", "#2f3e5c");
    private final AttributeValue oatmeal = AttributeValue.create(color, "오트밀", "#e3d9c6");
    private final AttributeValue size110 = AttributeValue.create(size, "110", null);
    private final AttributeValue size120 = AttributeValue.create(size, "120", null);
    private final ProductType colorPriced = ProductType.define("색상별 가격 의류", List.of(
        ProductTypeFixture.priceAffectingOption(color), ProductTypeFixture.sizeOption(size)));

    @Test
    @DisplayName("PRICE-03 가격 구분 묶음의 가격을 그 묶음의 옵션 전체에 넣는다")
    void putGroupPriceOnEveryOption() {
        product.addOptions(new Product.AddOptionsInfo(SELLER_ID, List.of(
            option(clothing.navy(), clothing.size110()),
            option(clothing.oatmeal(), clothing.size120())), clothingPrice(27_000L)), clothing.type());

        assertThat(product.getOptions()).extracting(ProductOption::getPrice).containsExactly(27_000L, 27_000L);
    }

    @Test
    @DisplayName("PRICE-03 옵션이 있는 상품에 같은 묶음 가격으로 옵션을 더 추가한다")
    void addMoreOptionsWithSamePrice() {
        product.addOptions(addOptions(List.of(option(clothing.navy(), clothing.size110()))), clothing.type());

        product.addOptions(addOptions(List.of(option(clothing.oatmeal(), clothing.size110()))), clothing.type());

        assertThat(product.getOptions()).hasSize(2);
    }

    @Test
    @DisplayName("PRICE-01 옵션이 속한 가격 구분 묶음에 가격이 없으면 거절한다")
    void rejectOptionWithoutPrice() {
        Product.AddOptionsInfo info = new Product.AddOptionsInfo(
            SELLER_ID, List.of(option(clothing.navy(), clothing.size110())), List.of());

        assertRejected(() -> product.addOptions(info, clothing.type()), ProductErrorCode.PRICE_GROUP_MISMATCH);
        assertThat(product.getOptions()).isEmpty();
    }

    @Test
    @DisplayName("PRICE-03 이미 있는 옵션과 같은 가격 구분 묶음인데 가격이 다르면 거절한다")
    void rejectPriceDifferentFromExistingOptions() {
        product.addOptions(addOptions(List.of(option(clothing.navy(), clothing.size110()))), clothing.type());
        Product.AddOptionsInfo info = new Product.AddOptionsInfo(
            SELLER_ID, List.of(option(clothing.navy(), clothing.size120())), clothingPrice(30_000L));

        assertRejected(
            () -> product.addOptions(info, clothing.type()), ProductErrorCode.EXISTING_OPTION_PRICE_MISMATCH);
        assertThat(product.getOptions()).hasSize(1);
    }

    @Test
    @DisplayName("PRICE-03 같은 가격 구분 묶음의 가격이 두 번 오면 거절한다")
    void rejectTwoPricesForSamePriceGroup() {
        Product.AddOptionsInfo info = new Product.AddOptionsInfo(
            SELLER_ID,
            List.of(option(clothing.navy(), clothing.size110())),
            List.of(new Product.PriceInfo(List.of(), 25_000L), new Product.PriceInfo(List.of(), 30_000L)));

        assertRejected(() -> product.addOptions(info, clothing.type()), ProductErrorCode.PRICE_GROUP_MISMATCH);
    }

    @Test
    @DisplayName("PRICE-01 가격 구분 항목이 없는 유형은 가격 구분 묶음이 빈 값 목록 하나이고 그 가격은 옵션 판매가다")
    void currentPricesWithoutPriceAffectingAttribute() {
        product.addOptions(new Product.AddOptionsInfo(SELLER_ID, List.of(
            option(clothing.navy(), clothing.size110()),
            option(clothing.oatmeal(), clothing.size120())), clothingPrice(27_000L)), clothing.type());

        assertThat(product.currentPrices(clothing.type()))
            .containsExactly(new Product.PriceInfo(List.of(), 27_000L));
    }

    @Test
    @DisplayName("PRICE-03 가격 구분 항목이 있는 유형은 옵션 순서대로 묶음마다 가격 하나를 만든다")
    void currentPricesPerPriceGroup() {
        Product colorPricedProduct = colorPricedProductWithOptions();

        assertThat(colorPricedProduct.currentPrices(colorPriced)).containsExactly(
            new Product.PriceInfo(List.of(navy), 25_000L),
            new Product.PriceInfo(List.of(oatmeal), 27_000L));
    }

    @Test
    @DisplayName("SALE-06 보관한 옵션의 가격 구분 묶음은 만들지 않는다")
    void currentPricesSkipArchivedOptions() {
        Product colorPricedProduct = colorPricedProductWithOptions();
        ProductOption oatmeal110 = colorPricedProduct.getOptions().get(1);
        forceSaleStatus(oatmeal110, ProductOptionStatus.ARCHIVED);

        assertThat(colorPricedProduct.currentPrices(colorPriced))
            .containsExactly(new Product.PriceInfo(List.of(navy), 25_000L));
    }

    @Test
    @DisplayName("PRICE-01 만든 가격 구분 묶음을 그대로 옵션 추가에 넣으면 옵션마다 원래 판매가가 들어간다")
    void currentPricesCanBeSentBack() {
        List<Product.PriceInfo> currentPrices = colorPricedProductWithOptions().currentPrices(colorPriced);
        Product another = Product.register(ProductFixture.productAWith(colorPriced, List.of()));

        another.addOptions(new Product.AddOptionsInfo(SELLER_ID,
            List.of(option(navy, size110), option(oatmeal, size110), option(navy, size120)), currentPrices),
            colorPriced);

        assertThat(another.getOptions()).extracting(ProductOption::getPrice)
            .containsExactly(25_000L, 27_000L, 25_000L);
    }

    @Test
    @DisplayName("PRICE-03 가격 구분 항목이 둘 이상이면 가격 구분 묶음의 값을 유형의 항목 순서로 담는다")
    void currentPricesKeepTypeOrderInGroup() {
        Attribute material = ProductTypeFixture.mainMaterial();
        AttributeValue cotton = AttributeValue.create(material, "면", null);
        AttributeValue polyester = AttributeValue.create(material, "폴리에스테르", null);
        ProductType colorMaterialPriced = ProductType.define("색상 · 소재별 가격 의류", List.of(
            ProductTypeFixture.priceAffectingOption(color),
            ProductTypeFixture.priceAffectingOption(material),
            ProductTypeFixture.sizeOption(size)));
        Product colorMaterialProduct = Product.register(ProductFixture.productAWith(colorMaterialPriced, List.of()));
        colorMaterialProduct.addOptions(new Product.AddOptionsInfo(SELLER_ID,
            List.of(option(cotton, navy, size110), option(polyester, oatmeal, size110)),
            List.of(new Product.PriceInfo(List.of(cotton, navy), 25_000L),
                new Product.PriceInfo(List.of(polyester, oatmeal), 27_000L))),
            colorMaterialPriced);

        assertThat(colorMaterialProduct.currentPrices(colorMaterialPriced)).containsExactly(
            new Product.PriceInfo(List.of(navy, cotton), 25_000L),
            new Product.PriceInfo(List.of(oatmeal, polyester), 27_000L));
    }

    private Product colorPricedProductWithOptions() {
        Product colorPricedProduct = Product.register(ProductFixture.productAWith(colorPriced, List.of()));
        colorPricedProduct.addOptions(new Product.AddOptionsInfo(SELLER_ID,
            List.of(option(navy, size110), option(oatmeal, size110), option(navy, size120)),
            List.of(new Product.PriceInfo(List.of(oatmeal), 27_000L), new Product.PriceInfo(List.of(navy), 25_000L))),
            colorPriced);
        return colorPricedProduct;
    }
}
