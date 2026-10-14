package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.addOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.clothingOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.option;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.productA;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.addSizeSpecs;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.spec;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.top110Measurements;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.top120Measurements;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.ClothingOptions;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductSaleStartTest {

    private final ClothingOptions clothing = clothingOptions();
    private final Product product = productA(clothing);
    private final Category top = CategoryFixture.top(CategoryFixture.clothing(), clothing.type());

    @Test
    @DisplayName("SALE-03 판매 시작 조건을 채운 판매대기 상품은 판매를 시작해 판매중이 된다")
    void startSaleOfReadyProduct() {
        withOptionsAndSizeSpecs110And120();

        boolean started = product.startSale(SELLER_ID, top, clothing.type());

        assertThat(started).isTrue();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("이미 판매중인 상품에 다시 판매를 시작하면 아무것도 바꾸지 않는다 (API 명세 3.7, 재시도 대비)")
    void startSaleAgainChangesNothing() {
        withOptionsAndSizeSpecs110And120();
        product.startSale(SELLER_ID, top, clothing.type());

        boolean started = product.startSale(SELLER_ID, top, clothing.type());

        assertThat(started).isFalse();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("SALE-03 옵션이 없는 상품은 판매를 시작할 수 없다")
    void rejectStartSaleWithoutOptions() {

        assertSaleStartRejected("판매를 시작할 수 없습니다. 옵션이 하나 이상 있어야 합니다.");
    }

    // 상품 A에 네이비 110 · 120 옵션과, 두 사이즈의 필수 실측 · 권장 키를 모두 넣는다 (상품 규칙 7.1)
    private void withOptionsAndSizeSpecs110And120() {
        product.addOptions(addOptions(List.of(
            option(clothing.navy(), clothing.size110()),
            option(clothing.navy(), clothing.size120()))), clothing.type());
        product.addSizeSpecs(addSizeSpecs(List.of(
            spec(clothing.size110(), 105, 115, top110Measurements()),
            spec(clothing.size120(), 115, 125, top120Measurements()))), top, clothing.type());
    }

    // 판매 시작이 409로 거절되고 빠진 항목이 detail에 적히며, 상품은 판매대기로 남는지 확인한다
    private void assertSaleStartRejected(String detail) {
        assertThatThrownBy(() -> product.startSale(SELLER_ID, top, clothing.type()))
            .isInstanceOfSatisfying(BusinessException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.SALE_START_CONDITION_NOT_MET);
                assertThat(e.getDetail()).isEqualTo(detail);
            });
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SALE_PENDING);
    }
}
