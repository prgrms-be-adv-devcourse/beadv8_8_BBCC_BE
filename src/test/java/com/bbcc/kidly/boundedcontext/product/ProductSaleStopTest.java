package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.OTHER_SELLER_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.addOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.assertRejected;
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
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 판매자와 관리자가 상품의 판매를 중지하거나 다시 여는 업무 메서드를 확인한다.
 */
class ProductSaleStopTest {

    private final ClothingOptions clothing = clothingOptions();
    private final Product product = productA(clothing);
    private final Category top = CategoryFixture.top(CategoryFixture.clothing(), clothing.type());

    @Test
    @DisplayName("판매자가 판매중 상품의 판매를 중지하면 판매중지가 된다")
    void stopSaleOfOnSaleProduct() {
        withSaleStarted();

        boolean stopped = product.stopSale(SELLER_ID);

        assertThat(stopped).isTrue();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.STOPPED);
    }

    @Test
    @DisplayName("이미 판매중지인 상품의 판매를 다시 중지하면 아무것도 바꾸지 않는다 (재시도 대비)")
    void stopSaleAgainChangesNothing() {
        withSaleStarted();
        product.stopSale(SELLER_ID);

        boolean stopped = product.stopSale(SELLER_ID);

        assertThat(stopped).isFalse();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.STOPPED);
    }

    @Test
    @DisplayName("판매대기 상품은 판매를 시작하지 않아서 중지할 수 없다")
    void rejectStopSaleOfPendingProduct() {
        withSaleStartInputs();

        assertRejected(() -> product.stopSale(SELLER_ID), ProductErrorCode.SALE_NOT_STARTED);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SALE_PENDING);
    }

    @Test
    @DisplayName("OPT-08 상품을 등록한 판매자가 아니면 판매를 중지할 수 없다")
    void rejectStopSaleFromOtherSeller() {
        withSaleStarted();

        assertRejected(() -> product.stopSale(OTHER_SELLER_ID), ProductErrorCode.NOT_PRODUCT_OWNER);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("판매를 중지하는 판매자 ID가 없으면 코드가 잘못 호출된 것이라 NullPointerException이다")
    void rejectStopSaleWithoutSellerId() {
        withSaleStarted();

        assertThatThrownBy(() -> product.stopSale(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("SALE-03 판매자가 멈춘 상품은 판매 시작 조건을 다시 검사해 판매를 다시 시작한다")
    void restartSaleOfStoppedProduct() {
        withSaleStarted();
        product.stopSale(SELLER_ID);

        boolean started = product.startSale(SELLER_ID, top, clothing.type());

        assertThat(started).isTrue();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("SALE-11 관리자가 판매중 상품의 판매를 중지하면 판매중지가 되고 관리자가 멈춘 상품으로 표시한다")
    void stopSaleByAdmin() {
        withSaleStarted();

        boolean stopped = product.stopSaleByAdmin();

        assertThat(stopped).isTrue();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.STOPPED);
        assertThat(product.isStoppedByAdmin()).isTrue();
    }

    @Test
    @DisplayName("SALE-11 판매자가 멈춘 상품을 관리자가 중지하면 판매 상태는 그대로 두고 관리자가 멈춘 상품으로만 표시한다")
    void stopSaleByAdminOfSellerStoppedProduct() {
        withSaleStarted();
        product.stopSale(SELLER_ID);

        boolean stopped = product.stopSaleByAdmin();

        assertThat(stopped).isFalse();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.STOPPED);
        assertThat(product.isStoppedByAdmin()).isTrue();
    }

    @Test
    @DisplayName("SALE-11 관리자도 판매를 시작하지 않은 판매대기 상품은 중지할 수 없다")
    void rejectStopSaleByAdminOfPendingProduct() {
        withSaleStartInputs();

        assertRejected(product::stopSaleByAdmin, ProductErrorCode.SALE_NOT_STARTED);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SALE_PENDING);
        assertThat(product.isStoppedByAdmin()).isFalse();
    }

    @Test
    @DisplayName("SALE-11 관리자가 멈춘 상품은 판매자가 다시 판매를 시작할 수 없다")
    void rejectRestartSaleOfAdminStoppedProduct() {
        withSaleStarted();
        product.stopSaleByAdmin();

        assertRejected(() -> product.startSale(SELLER_ID, top, clothing.type()), ProductErrorCode.STOPPED_BY_ADMIN);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.STOPPED);
    }

    // 상품 A에 네이비 110 · 120 옵션과 두 사이즈의 필수 실측 · 권장 키를 넣고 판매를 시작한다
    private void withSaleStarted() {
        withSaleStartInputs();
        product.startSale(SELLER_ID, top, clothing.type());
    }

    // 상품 A에 네이비 110 · 120 옵션과 두 사이즈의 필수 실측 · 권장 키를 넣는다. 판매 시작 조건을 채운 판매대기 상품이 된다
    private void withSaleStartInputs() {
        product.addOptions(addOptions(List.of(
            option(clothing.navy(), clothing.size110()),
            option(clothing.navy(), clothing.size120()))), clothing.type());
        product.addSizeSpecs(addSizeSpecs(List.of(
            spec(clothing.size110(), 105, 115, top110Measurements()),
            spec(clothing.size120(), 115, 125, top120Measurements()))), top, clothing.type());
    }
}
