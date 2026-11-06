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
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 판매자가 상품을 등록 취소하고, 관리자가 등록 취소한 상품을 되돌리는 업무 메서드를 확인한다.
 */
class ProductDelistTest {

    private final ClothingOptions clothing = clothingOptions();
    private final Product product = productA(clothing);
    private final Category top = CategoryFixture.top(CategoryFixture.clothing(), clothing.type());

    @ParameterizedTest(name = "{0}")
    @MethodSource("productsBeforeDelist")
    @DisplayName("SALE-13 등록한 판매자는 판매대기 · 판매중 · 판매중지 상품을 등록 취소하고, 관리자 판매중지 표시는 그대로 둔다")
    void delistFromAnyStatus(Consumer<ProductDelistTest> given) {
        given.accept(this);
        boolean stoppedByAdminBefore = product.isStoppedByAdmin();

        boolean delisted = product.delist(SELLER_ID);

        assertThat(delisted).isTrue();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.DELISTED);
        assertThat(product.isStoppedByAdmin()).isEqualTo(stoppedByAdminBefore);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("productsBeforeDelist")
    @DisplayName("SALE-13 이미 등록 취소한 상품을 다시 등록 취소하면 관리자 판매중지 표시까지 아무것도 바꾸지 않는다 (재시도 대비)")
    void delistAgainChangesNothing(Consumer<ProductDelistTest> given) {
        given.accept(this);
        product.delist(SELLER_ID);
        boolean stoppedByAdminBefore = product.isStoppedByAdmin();

        boolean delisted = product.delist(SELLER_ID);

        assertThat(delisted).isFalse();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.DELISTED);
        assertThat(product.isStoppedByAdmin()).isEqualTo(stoppedByAdminBefore);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("productsOfOtherSeller")
    @DisplayName("OPT-08 상품을 등록한 판매자가 아니면 등록 취소할 수 없다 (이미 등록 취소한 상품이어도 403이 먼저다)")
    void rejectDelistFromOtherSeller(Consumer<ProductDelistTest> given) {
        given.accept(this);
        ProductStatus statusBefore = product.getStatus();

        assertRejected(() -> product.delist(OTHER_SELLER_ID), ProductErrorCode.NOT_PRODUCT_OWNER);
        assertThat(product.getStatus()).isEqualTo(statusBefore);
    }

    @Test
    @DisplayName("등록 취소의 판매자가 null이면 코드가 잘못 호출된 것이라 거절하고 아무것도 바꾸지 않는다")
    void rejectDelistWithNull() {
        withSaleStarted();

        assertThatThrownBy(() -> product.delist(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("필수입니다");
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("productsBeforeDelist")
    @DisplayName("SALE-14 관리자가 등록 취소한 상품을 되돌리면 판매중지가 되고, 관리자 판매중지 표시는 그대로 둔다")
    void revertDelistByAdminToStopped(Consumer<ProductDelistTest> given) {
        given.accept(this);
        boolean stoppedByAdminBefore = product.isStoppedByAdmin();
        product.delist(SELLER_ID);

        product.revertDelistByAdmin();

        assertThat(product.getStatus()).isEqualTo(ProductStatus.STOPPED);
        assertThat(product.isStoppedByAdmin()).isEqualTo(stoppedByAdminBefore);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("productsBeforeDelist")
    @DisplayName("SALE-14 등록 취소하지 않은 상품은 관리자도 되돌릴 수 없다")
    void rejectRevertOfNotDelisted(Consumer<ProductDelistTest> given) {
        given.accept(this);
        ProductStatus statusBefore = product.getStatus();
        boolean stoppedByAdminBefore = product.isStoppedByAdmin();

        assertRejected(product::revertDelistByAdmin, ProductErrorCode.PRODUCT_NOT_DELISTED);
        assertThat(product.getStatus()).isEqualTo(statusBefore);
        assertThat(product.isStoppedByAdmin()).isEqualTo(stoppedByAdminBefore);
    }

    static Stream<Named<Consumer<ProductDelistTest>>> productsOfOtherSeller() {
        return Stream.of(
            Named.of("판매대기", ProductDelistTest::withSaleStartInputs),
            Named.of("판매중", ProductDelistTest::withSaleStarted),
            Named.of("등록 취소", test -> {
                test.withSaleStarted();
                test.product.delist(SELLER_ID);
            }));
    }

    static Stream<Named<Consumer<ProductDelistTest>>> productsBeforeDelist() {
        return Stream.of(
            Named.of("판매대기", ProductDelistTest::withSaleStartInputs),
            Named.of("판매중", ProductDelistTest::withSaleStarted),
            Named.of("판매자가 판매중지한 상품", test -> {
                test.withSaleStarted();
                test.product.stopSale(SELLER_ID);
            }),
            Named.of("관리자가 판매중지한 상품", test -> {
                test.withSaleStarted();
                test.product.stopSaleByAdmin();
            }));
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
