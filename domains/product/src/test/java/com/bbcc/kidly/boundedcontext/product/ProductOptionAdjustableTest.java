package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.OTHER_SELLER_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.assertRejected;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 판매자가 옵션의 재고를 조정할 수 있는지 확인한다.
 */
class ProductOptionAdjustableTest {

    private final Product product = ProductUpdateFixture.onSaleProductA().product();
    private final ProductOption option = product.getOptions().get(0);

    @Test
    @DisplayName("SALE-15 등록 취소한 상품의 옵션은 판매자가 재고를 조정할 수 없다")
    void rejectAdjustOfDelistedProductOption() {
        product.delist(SELLER_ID);

        assertRejected(() -> option.validateAdjustableBy(SELLER_ID), ProductErrorCode.PRODUCT_DELISTED);
    }

    @Test
    @DisplayName("OPT-08 · SALE-15 다른 판매자의 옵션이면 등록 취소 상품이어도 403이 먼저다")
    void rejectAdjustFromOtherSellerBeforeDelisted() {
        product.delist(SELLER_ID);

        assertRejected(() -> option.validateAdjustableBy(OTHER_SELLER_ID), ProductErrorCode.NOT_PRODUCT_OWNER);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("productsNotDelisted")
    @DisplayName("SALE-15 등록 취소하지 않은 상품의 옵션은 등록한 판매자가 재고를 조정할 수 있다")
    void allowAdjustOfNotDelisted(Supplier<Product> given) {
        ProductOption notDelistedOption = given.get().getOptions().get(0);

        assertThatCode(() -> notDelistedOption.validateAdjustableBy(SELLER_ID)).doesNotThrowAnyException();
    }

    static Stream<Named<Supplier<Product>>> productsNotDelisted() {
        return Stream.of(
            Named.of("판매대기", () -> ProductUpdateFixture.salePendingProductA().product()),
            Named.of("판매중", () -> ProductUpdateFixture.onSaleProductA().product()),
            Named.of("판매자가 판매중지한 상품", () -> {
                Product stopped = ProductUpdateFixture.onSaleProductA().product();
                stopped.stopSale(SELLER_ID);
                return stopped;
            }),
            Named.of("관리자가 판매중지한 상품", () -> {
                Product stopped = ProductUpdateFixture.onSaleProductA().product();
                stopped.stopSaleByAdmin();
                return stopped;
            }));
    }
}
