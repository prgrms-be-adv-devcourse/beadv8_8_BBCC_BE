package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.clothingPrice;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.product.ProductUpdateFixture.ProductA;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import java.util.List;
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
}
