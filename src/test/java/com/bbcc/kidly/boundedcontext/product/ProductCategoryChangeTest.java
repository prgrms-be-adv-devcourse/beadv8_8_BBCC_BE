package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.assertRejected;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.clothingPrice;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.ProductUpdateFixture.ProductA;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOptionValue;
import com.bbcc.kidly.boundedcontext.product.domain.product.SizeSpec;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
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

    @Test
    @DisplayName("판매대기 상품을 같은 유형의 카테고리(상의 → 아우터)로 바꾸면서 옵션 · 가격 · 실측을 함께 보내면 카테고리를 바꾸고 같은 옵션은 그대로 둔다")
    void changePendingCategoryWithSaleInputs() {
        ProductA productA = ProductUpdateFixture.salePendingProductA();
        List<ProductOption> optionsBefore = List.copyOf(productA.product().getOptions());

        Product.UpdateResult result = productA.product().update(
            productA.updateWithCategory(productA.outer()), productA.currentSaleInputs());

        assertThat(result.changed()).isTrue();
        assertThat(productA.product().getCategoryId()).isEqualTo(ProductUpdateFixture.OUTER_ID);
        assertThat(productA.product().getOptions()).containsExactlyElementsOf(optionsBefore);
    }

    @Test
    @DisplayName("OPT-06 예외: 다른 유형으로 바꿀 때 기존 옵션을 보관하는 규칙은 주문이 가리키는 옵션을 지키려는 것이라, "
        + "판매대기 상품(상의 → 운동화)은 새 유형의 옵션 · 실측으로 바꿔 넣는다")
    void changeTypeWithNewSaleInputs() {
        ProductA productA = ProductUpdateFixture.salePendingProductA();
        ProductUpdateFixture.Shoes shoes = productA.shoes();

        Product.UpdateResult result = productA.product().update(productA.updateWithCategory(shoes.sneakers()),
            new Product.SaleInputsInfo(
                List.of(new Product.OptionInfo(List.of(shoes.black(), shoes.size200()), null)),
                clothingPrice(39_000L),
                List.of(SizeSpecFixture.spec(
                    shoes.size200(), 100, 110, Map.of("FOOT_LENGTH", new BigDecimal("200"))))));

        assertThat(result.changed()).isTrue();
        assertThat(productA.product().getCategoryId()).isEqualTo(ProductUpdateFixture.SNEAKERS_ID);
        assertThat(productA.product().getOptions()).singleElement()
            .satisfies(option -> assertThat(option.getValues()).extracting(ProductOptionValue::getAttributeValue)
                .containsExactlyInAnyOrder(shoes.black(), shoes.size200()))
            .extracting(ProductOption::getPrice).isEqualTo(39_000L);
        assertThat(productA.product().getSizeSpecs()).singleElement()
            .extracting(SizeSpec::getMeasurements).isEqualTo(Map.of("FOOT_LENGTH", new BigDecimal("200")));
    }
}
