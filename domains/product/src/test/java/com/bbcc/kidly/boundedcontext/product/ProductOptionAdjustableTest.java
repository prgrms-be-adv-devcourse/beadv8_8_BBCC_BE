package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.OTHER_SELLER_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.assertRejected;

import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
