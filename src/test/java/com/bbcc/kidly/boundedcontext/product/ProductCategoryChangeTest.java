package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.ProductUpdateFixture.ProductA;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 상품을 고치면서 카테고리를 바꾸는 동작을 확인한다.
 */
class ProductCategoryChangeTest {

    static Stream<Named<Function<ProductA, Category>>> otherCategories() {
        return Stream.of(
            Named.of("같은 유형이고 필수 실측이 같은 아우터", ProductA::outer),
            Named.of("같은 유형이고 필수 실측이 다른 하의", ProductA::bottom),
            Named.of("다른 유형인 운동화", ProductA::sneakers));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("otherCategories")
    @DisplayName("판매를 시작한 상품은 어느 카테고리로 바꾸든 409로 거절하고 카테고리와 옵션은 그대로다")
    void rejectCategoryChangeAfterSaleStart(Function<ProductA, Category> newCategory) {
        ProductA productA = ProductUpdateFixture.onSaleProductA();
        List<ProductOption> optionsBefore = List.copyOf(productA.product().getOptions());

        assertRejected(() -> productA.product().update(productA.updateWithCategory(newCategory.apply(productA))),
            ProductErrorCode.CATEGORY_CHANGE_NOT_ALLOWED);
        assertThat(productA.product().getCategoryId()).isEqualTo(ProductUpdateFixture.TOP_ID);
        assertThat(productA.product().getOptions()).containsExactlyElementsOf(optionsBefore);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("otherCategories")
    @DisplayName("판매대기 상품은 어느 카테고리로 바꾸든 옵션 · 가격 · 실측을 함께 보내지 않으면 400으로 거절하고 카테고리는 그대로다")
    void rejectPendingCategoryChangeWithoutSaleInputs(Function<ProductA, Category> newCategory) {
        ProductA productA = ProductUpdateFixture.salePendingProductA();

        assertRejected(() -> productA.product().update(productA.updateWithCategory(newCategory.apply(productA))),
            ProductErrorCode.SALE_INPUTS_REQUIRED_FOR_CATEGORY_CHANGE);
        assertThat(productA.product().getCategoryId()).isEqualTo(ProductUpdateFixture.TOP_ID);
    }
}
