package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOptionStatus;
import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeInputType;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeRole;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.springframework.test.util.ReflectionTestUtils;


final class ProductOptionFixture {

    static final long OTHER_SELLER_ID = 3L;
    static final long CLOTHING_PRICE = 25_000L;

    private ProductOptionFixture() {
    }

    record ClothingOptions(
        ProductType type,
        Attribute mainMaterial,
        AttributeValue cotton,
        AttributeValue navy,
        AttributeValue oatmeal,
        AttributeValue size110,
        AttributeValue size120,
        AttributeValue sizeFree,
        AttributeValue size105,
        AttributeValue stripe) {
    }

    static ClothingOptions clothingOptions() {
        Attribute color = ProductTypeFixture.color();
        Attribute size = ProductTypeFixture.size();
        Attribute pattern = Attribute.create("무늬", "PATTERN", AttributeInputType.LIST);
        Attribute mainMaterial = ProductTypeFixture.mainMaterial();
        AttributeValue cotton = AttributeValue.create(mainMaterial, "면", null);
        AttributeValue navy = AttributeValue.create(color, "네이비", "#2f3e5c");
        AttributeValue oatmeal = AttributeValue.create(color, "오트밀", "#e3d9c6");
        AttributeValue size110 = AttributeValue.create(size, "110", null);
        AttributeValue size120 = AttributeValue.create(size, "120", null);
        AttributeValue sizeFree = AttributeValue.create(size, "FREE", null);
        AttributeValue size105 = AttributeValue.create(size, "105", null);
        AttributeValue stripe = AttributeValue.create(pattern, "스트라이프", null);
        ProductType type = ProductType.define("의류", List.of(
            ProductTypeFixture.requiredOption(color),
            new ProductType.LinkInfo(
                size, AttributeRole.OPTION, true, false, true, false, List.of(size110, size120, sizeFree)),
            ProductTypeFixture.optionalOption(pattern),
            ProductTypeFixture.requiredInfo(mainMaterial)));
        return new ClothingOptions(
            type, mainMaterial, cotton, navy, oatmeal, size110, size120, sizeFree, size105, stripe);
    }

    static Product productA(ClothingOptions clothing) {
        return Product.register(ProductFixture.productAWith(clothing.type(), List.of(
            new Product.InfoValueInfo(clothing.mainMaterial(), List.of(clothing.cotton()), null))));
    }

    static Product.OptionInfo option(AttributeValue... values) {
        return new Product.OptionInfo(List.of(values), null);
    }

    static List<Product.PriceInfo> clothingPrice(long price) {
        return List.of(new Product.PriceInfo(List.of(), price));
    }

    static Product.AddOptionsInfo addOptions(List<Product.OptionInfo> options) {
        return new Product.AddOptionsInfo(SELLER_ID, options, clothingPrice(CLOTHING_PRICE));
    }

    static ProductOption navy110(ClothingOptions clothing) {
        Product product = productA(clothing);
        product.addOptions(addOptions(List.of(option(clothing.navy(), clothing.size110()))), clothing.type());
        return product.getOptions().get(0);
    }

    static void forceSaleStatus(ProductOption option, ProductOptionStatus status) {
        ReflectionTestUtils.setField(option, "saleStatus", status);
    }

    record Requests(List<Product.OptionInfo> existing, List<Product.OptionInfo> requested) {
    }

    static void assertRejected(ThrowingCallable call, ProductErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}
