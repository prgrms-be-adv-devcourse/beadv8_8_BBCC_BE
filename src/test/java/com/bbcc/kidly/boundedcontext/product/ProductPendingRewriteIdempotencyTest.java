package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.assertRejected;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.clothingPrice;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.top110Measurements;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.ProductUpdateFixture.ProductA;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.boundedcontext.product.domain.product.SizeSpec;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 판매대기 상품에 옵션 · 가격 · 실측을 다시 보낼 때 바뀐 것이 있는지 판단하는 동작과 version 확인을 본다.
 * 상품 A는 옵션 네이비 · 110과 네이비 · 120(25,000원, SKU 없음)과 두 사이즈의 상의 실측이 있고, version은 3이다.
 */
class ProductPendingRewriteIdempotencyTest {

    private static final long STALE_VERSION = ProductUpdateFixture.VERSION - 1;

    static Stream<Named<Function<ProductA, Product.UpdateResult>>> sameSaleInputs() {
        return Stream.of(
            Named.of("옵션 · 실측을 다른 순서로 보냄", productA -> productA.product().update(productA.sameUpdate(),
                new Product.SaleInputsInfo(productA.currentSaleInputs().options().reversed(),
                    clothingPrice(ProductOptionFixture.CLOTHING_PRICE), productA.currentSizeSpecs().reversed()))),
            Named.of("실측 값을 소수점까지 적어 보냄(42 → 42.0)", productA -> productA.product().update(
                productA.sameUpdate(), withSizeSpecs(productA, oneDecimalSizeSpecs(productA)))),
            Named.of("낡은 version으로 보냄", productA -> productA.product().update(
                productA.updateWith(STALE_VERSION, productA.top(), productA.product().getName()),
                productA.currentSaleInputs())));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sameSaleInputs")
    @DisplayName("판매대기 상품에 지금과 같은 옵션 · 가격 · 실측을 다시 보내면 version을 보지 않고 아무것도 바꾸지 않는다")
    void keepWhenSaleInputsUnchanged(Function<ProductA, Product.UpdateResult> update) {
        ProductA productA = ProductUpdateFixture.salePendingProductA();
        List<ProductOption> optionsBefore = List.copyOf(productA.product().getOptions());
        List<SizeSpec> sizeSpecsBefore = List.copyOf(productA.product().getSizeSpecs());

        Product.UpdateResult result = update.apply(productA);

        assertThat(result.changed()).isFalse();
        assertThat(productA.product().getOptions()).containsExactlyElementsOf(optionsBefore);
        assertThat(productA.product().getSizeSpecs()).containsExactlyElementsOf(sizeSpecsBefore);
        assertThat(productA.product().getSizeSpecs().get(0).getMeasurements()).isEqualTo(top110Measurements());
    }

    @Test
    @DisplayName("판매대기 상품에 옵션은 같고 실측 값만 다르게 보내도 바뀐 것으로 보고 실측 값을 맞춘다")
    void changeWhenOnlySizeSpecValuesDiffer() {
        ProductA productA = ProductUpdateFixture.salePendingProductA();
        Map<String, BigDecimal> measurements = new HashMap<>(top110Measurements());
        measurements.put("TOTAL_LENGTH", new BigDecimal("50"));

        Product.UpdateResult result = productA.product().update(productA.sameUpdate(), withSizeSpecs(productA, List.of(
            SizeSpecFixture.spec(productA.clothing().size110(), 105, 115, measurements),
            productA.currentSizeSpecs().get(1))));

        assertThat(result.changed()).isTrue();
        assertThat(productA.product().getSizeSpecs().get(0).getMeasurements()).isEqualTo(measurements);
    }

    @Test
    @DisplayName("판매대기 상품에 옵션 · 가격을 바꿔 보내면서 version이 낡았으면 409로 거절하고 아무것도 바꾸지 않는다")
    void rejectStaleVersionWhenSaleInputsChange() {
        ProductA productA = ProductUpdateFixture.salePendingProductA();

        assertRejected(() -> productA.product().update(
                productA.updateWith(STALE_VERSION, productA.top(), productA.product().getName()),
                new Product.SaleInputsInfo(productA.currentSaleInputs().options(), clothingPrice(27_000L),
                    productA.currentSizeSpecs())),
            ProductErrorCode.PRODUCT_VERSION_MISMATCH);
        assertThat(productA.product().getOptions()).extracting(ProductOption::getPrice)
            .containsOnly(ProductOptionFixture.CLOTHING_PRICE);
    }

    private static Product.SaleInputsInfo withSizeSpecs(ProductA productA, List<Product.SizeSpecInfo> sizeSpecs) {
        return new Product.SaleInputsInfo(productA.currentSaleInputs().options(),
            clothingPrice(ProductOptionFixture.CLOTHING_PRICE), sizeSpecs);
    }

    // 지금 실측과 같은 길이를 소수점 한 자리까지 적은 실측이다(42 → 42.0)
    private static List<Product.SizeSpecInfo> oneDecimalSizeSpecs(ProductA productA) {
        return productA.currentSizeSpecs().stream()
            .map(spec -> SizeSpecFixture.spec(spec.sizeValue(), spec.minHeightCm(), spec.maxHeightCm(),
                spec.measurements().entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().setScale(1)))))
            .toList();
    }
}
