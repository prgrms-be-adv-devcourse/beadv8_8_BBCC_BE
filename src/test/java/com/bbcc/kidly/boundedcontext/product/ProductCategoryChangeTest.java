package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.ProductUpdateFixture.ProductA;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 상품을 고치면서 카테고리를 바꾸는 동작을 확인한다. 옵션 · 실측을 함께 바꿔 넣지 않는 경우다.
 */
class ProductCategoryChangeTest {

    @Test
    @DisplayName("OPT-06 판매중 상품을 같은 유형이고 필수 실측이 같은 카테고리(상의 → 아우터)로 바꾸면 카테고리만 바뀌고 옵션은 그대로다")
    void changeToSameTypeCategory() {
        ProductA productA = ProductUpdateFixture.onSaleProductA();
        List<ProductOption> optionsBefore = List.copyOf(productA.product().getOptions());

        boolean changed = productA.product().update(productA.updateWithCategory(productA.outer()));

        assertThat(changed).isTrue();
        assertThat(productA.product().getCategoryId()).isEqualTo(ProductUpdateFixture.OUTER_ID);
        assertThat(productA.product().getOptions()).containsExactlyElementsOf(optionsBefore);
    }

    @Test
    @DisplayName("SIZE-04 판매중 상품을 필수 실측이 다른 카테고리(상의 → 하의)로 바꾸면 409로 거절하고 카테고리는 그대로다")
    void rejectCategoryWithMissingMeasurements() {
        ProductA productA = ProductUpdateFixture.onSaleProductA();

        assertRejected(() -> productA.product().update(productA.updateWithCategory(productA.bottom())),
            ProductErrorCode.MEASUREMENTS_MISSING_FOR_CATEGORY);
        assertThat(productA.product().getCategoryId()).isEqualTo(ProductUpdateFixture.TOP_ID);
    }

    @Test
    @DisplayName("판매중 상품을 다른 상품 유형의 카테고리(상의 → 운동화)로 바꾸면 409로 거절하고 카테고리는 그대로다")
    void rejectOtherTypeCategoryAfterSaleStart() {
        ProductA productA = ProductUpdateFixture.onSaleProductA();

        assertRejected(() -> productA.product().update(productA.updateWithCategory(productA.sneakers())),
            ProductErrorCode.PRODUCT_TYPE_CHANGE_NOT_ALLOWED);
        assertThat(productA.product().getCategoryId()).isEqualTo(ProductUpdateFixture.TOP_ID);
    }
}
