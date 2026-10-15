package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.assertRejected;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.clothingPrice;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.product.ProductUpdateFixture.ProductA;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOptionValue;
import com.bbcc.kidly.boundedcontext.product.domain.product.SizeSpec;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 판매대기 상품을 이어서 쓸 때 옵션 · 가격 · 실측을 받은 값으로 바꿔 넣는 동작을 확인한다.
 * 상품 A는 옵션 네이비 · 110과 네이비 · 120(25,000원, SKU 없음)과 두 사이즈의 상의 실측이 있다.
 */
class ProductPendingRewriteTest {

    @Test
    @DisplayName("OPT-03 판매대기 상품은 값 조합이 같은 옵션을 그대로 두고 SKU 코드와 판매가만 받은 값으로 맞춘다")
    void keepOptionsWithSameValues() {
        ProductA productA = ProductUpdateFixture.salePendingProductA();
        List<ProductOption> optionsBefore = List.copyOf(productA.product().getOptions());

        boolean changed = productA.product().update(productA.sameUpdate(), new Product.SaleInputsInfo(
            List.of(
                new Product.OptionInfo(List.of(productA.clothing().navy(), productA.clothing().size110()), "A-NV-110"),
                new Product.OptionInfo(List.of(productA.clothing().navy(), productA.clothing().size120()), "A-NV-120")),
            clothingPrice(27_000L),
            productA.currentSizeSpecs()));

        assertThat(changed).isTrue();
        assertThat(productA.product().getOptions()).containsExactlyElementsOf(optionsBefore);
        assertThat(productA.product().getOptions())
            .extracting(ProductOption::getSkuCode, ProductOption::getPrice)
            .containsExactly(
                tuple("A-NV-110", 27_000L),
                tuple("A-NV-120", 27_000L));
    }

    @Test
    @DisplayName("OPT-05 판매대기 상품은 주문이 없으므로 받은 값 조합에 없는 옵션을 뺀다")
    void removeOptionsNotRequested() {
        ProductA productA = ProductUpdateFixture.salePendingProductA();
        ProductOption navy110 = productA.product().getOptions().get(0);

        boolean changed = productA.product().update(productA.sameUpdate(), new Product.SaleInputsInfo(
            List.of(new Product.OptionInfo(List.of(productA.clothing().navy(), productA.clothing().size110()), null)),
            clothingPrice(ProductOptionFixture.CLOTHING_PRICE),
            List.of(productA.currentSizeSpecs().get(0))));

        assertThat(changed).isTrue();
        assertThat(productA.product().getOptions()).containsExactly(navy110);
    }

    @Test
    @DisplayName("판매대기 상품은 받은 값 조합 중 지금 없는 조합을 새 옵션으로 만들고, 있던 옵션은 그대로 둔다")
    void addNewCombinations() {
        ProductA productA = ProductUpdateFixture.salePendingProductA();
        List<ProductOption> optionsBefore = List.copyOf(productA.product().getOptions());

        boolean changed = productA.product().update(productA.sameUpdate(), new Product.SaleInputsInfo(
            List.of(
                new Product.OptionInfo(List.of(productA.clothing().navy(), productA.clothing().size110()), null),
                new Product.OptionInfo(List.of(productA.clothing().navy(), productA.clothing().size120()), null),
                new Product.OptionInfo(
                    List.of(productA.clothing().oatmeal(), productA.clothing().size110()), "A-OT-110")),
            clothingPrice(ProductOptionFixture.CLOTHING_PRICE),
            productA.currentSizeSpecs()));

        assertThat(changed).isTrue();
        assertThat(productA.product().getOptions()).hasSize(3).startsWith(optionsBefore.toArray(ProductOption[]::new));
        ProductOption added = productA.product().getOptions().get(2);
        assertThat(added.getValues()).extracting(ProductOptionValue::getAttributeValue)
            .containsExactlyInAnyOrder(productA.clothing().oatmeal(), productA.clothing().size110());
        assertThat(added).extracting(ProductOption::getSkuCode, ProductOption::getPrice)
            .containsExactly("A-OT-110", ProductOptionFixture.CLOTHING_PRICE);
    }

    @Test
    @DisplayName("SIZE-04 판매대기 상품의 실측은 사이즈 값으로 짝지어 같은 사이즈는 객체를 두고 값만 맞추며, 받지 않은 사이즈의 실측은 뺀다")
    void rewriteSizeSpecsBySize() {
        ProductA productA = ProductUpdateFixture.salePendingProductA();
        SizeSpec spec110 = productA.product().getSizeSpecs().get(0);
        Map<String, BigDecimal> measurements = Map.of(
            "TOTAL_LENGTH", new BigDecimal("43"),
            "SHOULDER_WIDTH", new BigDecimal("31"),
            "CHEST_WIDTH", new BigDecimal("35"),
            "SLEEVE_LENGTH", new BigDecimal("39"));

        boolean changed = productA.product().update(productA.sameUpdate(), new Product.SaleInputsInfo(
            List.of(new Product.OptionInfo(List.of(productA.clothing().navy(), productA.clothing().size110()), null)),
            clothingPrice(ProductOptionFixture.CLOTHING_PRICE),
            List.of(SizeSpecFixture.spec(productA.clothing().size110(), 104, 114, measurements))));

        assertThat(changed).isTrue();
        assertThat(productA.product().getSizeSpecs()).containsExactly(spec110);
        assertThat(spec110.getMinHeightCm()).isEqualTo(104);
        assertThat(spec110.getMaxHeightCm()).isEqualTo(114);
        assertThat(spec110.getMeasurements()).isEqualTo(measurements);
    }

    @Test
    @DisplayName("판매대기 상품에 새 사이즈 옵션을 넣으면 그 사이즈의 실측도 새로 만든다")
    void addSizeSpecForNewSize() {
        ProductA productA = ProductUpdateFixture.salePendingProductA();
        Product.OptionInfo navy110 =
            new Product.OptionInfo(List.of(productA.clothing().navy(), productA.clothing().size110()), null);
        Product.OptionInfo navy120 =
            new Product.OptionInfo(List.of(productA.clothing().navy(), productA.clothing().size120()), null);
        List<Product.PriceInfo> price = clothingPrice(ProductOptionFixture.CLOTHING_PRICE);
        Product.SizeSpecInfo spec110 = productA.currentSizeSpecs().get(0);
        Product.SizeSpecInfo spec120 = productA.currentSizeSpecs().get(1);
        productA.product().update(productA.sameUpdate(),
            new Product.SaleInputsInfo(List.of(navy110), price, List.of(spec110)));

        boolean changed = productA.product().update(productA.sameUpdate(),
            new Product.SaleInputsInfo(List.of(navy110, navy120), price, List.of(spec110, spec120)));

        assertThat(changed).isTrue();
        assertThat(productA.product().getSizeSpecs())
            .extracting(SizeSpec::getMinHeightCm, SizeSpec::getMaxHeightCm, SizeSpec::getMeasurements)
            .containsExactly(
                tuple(105, 115, spec110.measurements()),
                tuple(115, 125, spec120.measurements()));
    }

    @Test
    @DisplayName("판매를 시작한 상품은 옵션 · 가격 · 실측을 보내면 409로 거절하고 아무것도 바꾸지 않는다")
    void rejectSaleInputsAfterSaleStart() {
        ProductA productA = ProductUpdateFixture.onSaleProductA();
        List<ProductOption> optionsBefore = List.copyOf(productA.product().getOptions());

        assertRejected(() -> productA.product().update(productA.sameUpdate(), new Product.SaleInputsInfo(
                List.of(new Product.OptionInfo(
                    List.of(productA.clothing().navy(), productA.clothing().size110()), null)),
                clothingPrice(27_000L),
                List.of(productA.currentSizeSpecs().get(0)))),
            ProductErrorCode.SALE_INPUTS_LOCKED);
        assertThat(productA.product().getOptions()).containsExactlyElementsOf(optionsBefore);
        assertThat(productA.product().getOptions()).extracting(ProductOption::getPrice)
            .containsOnly(ProductOptionFixture.CLOTHING_PRICE);
    }

    @Test
    @DisplayName("판매대기 상품을 이어 쓸 때도 등록처럼 옵션이 쓰는 사이즈마다 실측이 있어야 하고, 빠지면 거절하고 아무것도 바꾸지 않는다")
    void rejectMissingSaleStartInputs() {
        ProductA productA = ProductUpdateFixture.salePendingProductA();
        List<ProductOption> optionsBefore = List.copyOf(productA.product().getOptions());

        assertRejected(() -> productA.product().update(productA.sameUpdate(), new Product.SaleInputsInfo(
                List.of(
                    new Product.OptionInfo(List.of(productA.clothing().navy(), productA.clothing().size110()), "A-1"),
                    new Product.OptionInfo(List.of(productA.clothing().navy(), productA.clothing().size120()), "A-2")),
                clothingPrice(ProductOptionFixture.CLOTHING_PRICE),
                List.of(productA.currentSizeSpecs().get(0)))),
            ProductErrorCode.SALE_START_INPUT_MISSING);
        assertThat(productA.product().getOptions()).containsExactlyElementsOf(optionsBefore);
        assertThat(productA.product().getOptions()).extracting(ProductOption::getSkuCode).containsOnlyNulls();
        assertThat(productA.product().getSizeSpecs()).hasSize(2);
    }
}
