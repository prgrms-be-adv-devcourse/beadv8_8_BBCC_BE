package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.ProductUpdateFixture.ProductA;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductErrorCode;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import java.util.function.Function;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 등록 취소한 상품은 판매자도 관리자도 바꿀 수 없는지 확인한다. 관리자의 되돌리기만 등록 취소를 푼다.
 */
class ProductDelistedGuardTest {

    private final ProductA productA = ProductUpdateFixture.onSaleProductA();
    private final Product product = productA.product();

    @ParameterizedTest(name = "{0}")
    @MethodSource("guardedCalls")
    @DisplayName("SALE-15 등록 취소한 상품은 판매 상태 변경을 409로 거절하고 아무것도 바꾸지 않는다")
    void rejectChangeOfDelisted(Function<ProductA, ThrowingCallable> call) {
        product.delist(SELLER_ID);

        assertRejected(call.apply(productA), ProductErrorCode.PRODUCT_DELISTED);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.DELISTED);
        assertThat(product.isStoppedByAdmin()).isFalse();
    }

    @Test
    @DisplayName("SALE-15 등록 취소한 상품에 지금과 같은 값을 보낸 수정도 409로 거절한다")
    void rejectSameValueUpdateOfDelisted() {
        product.delist(SELLER_ID);

        assertRejected(() -> product.update(productA.sameUpdate()), ProductErrorCode.PRODUCT_DELISTED);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.DELISTED);
    }

    @Test
    @DisplayName("SALE-15 관리자가 판매중지한 뒤 등록 취소한 상품을 판매자가 다시 열면 관리자 판매중지보다 등록 취소로 거절한다")
    void delistedBeatsAdminStop() {
        product.stopSaleByAdmin();
        product.delist(SELLER_ID);

        assertRejected(() -> product.startSale(SELLER_ID, productA.top(), productA.clothing().type()),
            ProductErrorCode.PRODUCT_DELISTED);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.DELISTED);
        assertThat(product.isStoppedByAdmin()).isTrue();
    }

    static Stream<Named<Function<ProductA, ThrowingCallable>>> guardedCalls() {
        return Stream.of(
            Named.of("판매자의 판매 시작", a -> () -> a.product().startSale(SELLER_ID, a.top(), a.clothing().type())),
            Named.of("판매자의 판매 중지", a -> () -> a.product().stopSale(SELLER_ID)),
            Named.of("관리자의 판매 시작", a -> () -> a.product().startSaleByAdmin(a.top(), a.clothing().type())),
            Named.of("관리자의 판매중지", a -> () -> a.product().stopSaleByAdmin()));
    }
}
