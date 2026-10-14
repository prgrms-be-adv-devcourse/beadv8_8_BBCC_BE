package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.addOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.clothingOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.option;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.productA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.ClothingOptions;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductErrorCode;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ProductOptionSizeTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("mixedSizes")
    @DisplayName("SIZE-02 한 상품에 FREE와 숫자 사이즈를 섞으면 거절한다")
    void rejectFreeMixedWithNumberSize(Function<ClothingOptions, List<List<Product.OptionInfo>>> requests) {
        ClothingOptions clothing = clothingOptions();
        Product product = productA(clothing);
        List<List<Product.OptionInfo>> given = requests.apply(clothing);
        List<Product.OptionInfo> requested = given.getLast();
        for (List<Product.OptionInfo> existing : given.subList(0, given.size() - 1)) {
            product.addOptions(addOptions(existing), clothing.type());
        }
        int before = product.getOptions().size();

        assertThatThrownBy(() -> product.addOptions(addOptions(requested), clothing.type()))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.SIZE_FREE_MIXED));
        assertThat(product.getOptions()).hasSize(before);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("sizeFreeCases")
    @DisplayName("SIZE-02 사이즈 무관은 저장하지 않고, 옵션의 사이즈가 모두 FREE인지로 계산한다")
    void calculateSizeFree(Function<ClothingOptions, List<Product.OptionInfo>> options, boolean sizeFree) {
        ClothingOptions clothing = clothingOptions();
        Product product = productA(clothing);

        product.addOptions(addOptions(options.apply(clothing)), clothing.type());

        assertThat(product.isSizeFree()).isEqualTo(sizeFree);
    }

    static Stream<Arguments> sizeFreeCases() {
        return Stream.of(
            Arguments.of(Named.<Function<ClothingOptions, List<Product.OptionInfo>>>of("사이즈가 모두 FREE",
                clothing -> List.of(option(clothing.navy(), clothing.sizeFree()),
                    option(clothing.oatmeal(), clothing.sizeFree()))), true),
            Arguments.of(Named.<Function<ClothingOptions, List<Product.OptionInfo>>>of("숫자 사이즈",
                clothing -> List.of(option(clothing.navy(), clothing.size110()),
                    option(clothing.navy(), clothing.size120()))), false));
    }

    // 앞의 목록은 먼저 추가한 옵션이고, 마지막 목록이 거절될 요청이다
    static Stream<Named<Function<ClothingOptions, List<List<Product.OptionInfo>>>>> mixedSizes() {
        return Stream.of(
            Named.of("요청 안에서 FREE와 110", clothing -> List.of(
                List.of(option(clothing.navy(), clothing.sizeFree()), option(clothing.navy(), clothing.size110())))),
            Named.of("FREE 옵션이 있는데 110 옵션을 추가", clothing -> List.of(
                List.of(option(clothing.navy(), clothing.sizeFree())),
                List.of(option(clothing.oatmeal(), clothing.size110())))));
    }
}
