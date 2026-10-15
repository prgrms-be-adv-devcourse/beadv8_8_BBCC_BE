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
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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

    @ParameterizedTest(name = "{0}")
    @MethodSource("stoppedProducts")
    @DisplayName("이미 판매중지인 상품을 판매자가 다시 중지하면 관리자 판매중지 표시까지 아무것도 바꾸지 않는다 (재시도 대비)")
    void stopSaleAgainChangesNothing(Consumer<ProductSaleStopTest> given) {
        given.accept(this);
        boolean stoppedByAdminBefore = product.isStoppedByAdmin();

        boolean stopped = product.stopSale(SELLER_ID);

        assertThat(stopped).isFalse();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.STOPPED);
        assertThat(product.isStoppedByAdmin()).isEqualTo(stoppedByAdminBefore);
    }

    @Test
    @DisplayName("판매대기 상품은 판매를 시작하지 않아서 중지할 수 없다")
    void rejectStopSaleOfPendingProduct() {
        withSaleStartInputs();

        assertRejected(() -> product.stopSale(SELLER_ID), ProductErrorCode.SALE_NOT_STARTED);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SALE_PENDING);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("productsBeforeStop")
    @DisplayName("OPT-08 상품을 등록한 판매자가 아니면 판매대기든 판매중이든 판매를 중지할 수 없다 (판매대기여도 409보다 403이 먼저다)")
    void rejectStopSaleFromOtherSeller(Consumer<ProductSaleStopTest> given) {
        given.accept(this);
        ProductStatus statusBefore = product.getStatus();

        assertRejected(() -> product.stopSale(OTHER_SELLER_ID), ProductErrorCode.NOT_PRODUCT_OWNER);
        assertThat(product.getStatus()).isEqualTo(statusBefore);
    }

    @Test
    @DisplayName("SALE-03 판매자가 판매중지한 상품은 판매 시작 조건을 다시 검사해 판매를 다시 시작한다")
    void restartSaleOfStoppedProduct() {
        withSaleStarted();
        product.stopSale(SELLER_ID);

        boolean started = product.startSale(SELLER_ID, top, clothing.type());

        assertThat(started).isTrue();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("restartsWithoutSaleStartConditions")
    @DisplayName("SALE-03 판매중지 상품을 다시 열 때도 판매 시작 조건을 검사해, 실측이 없는 사이즈의 옵션이 생겼으면 거절한다")
    void rejectRestartSaleWithoutSaleStartConditions(Function<ProductSaleStopTest, ThrowingCallable> stopThenRestart) {
        ThrowingCallable restart = stopThenRestart.apply(this);
        boolean stoppedByAdminBefore = product.isStoppedByAdmin();

        assertRejected(restart, ProductErrorCode.SALE_START_CONDITION_NOT_MET);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.STOPPED);
        assertThat(product.isStoppedByAdmin()).isEqualTo(stoppedByAdminBefore);
    }

    @Test
    @DisplayName("SALE-11 관리자가 판매중 상품을 판매중지하면 관리자 판매중지 표시도 켠다")
    void stopSaleByAdmin() {
        withSaleStarted();

        boolean stopped = product.stopSaleByAdmin();

        assertThat(stopped).isTrue();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.STOPPED);
        assertThat(product.isStoppedByAdmin()).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("stoppedProducts")
    @DisplayName("SALE-11 이미 판매중지인 상품을 관리자가 판매중지하면 판매 상태는 그대로 두고 관리자 판매중지 표시만 켠다")
    void stopSaleByAdminOfStoppedProduct(Consumer<ProductSaleStopTest> given) {
        given.accept(this);

        boolean stopped = product.stopSaleByAdmin();

        assertThat(stopped).isFalse();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.STOPPED);
        assertThat(product.isStoppedByAdmin()).isTrue();
    }

    @Test
    @DisplayName("SALE-11 관리자도 판매를 시작하지 않은 판매대기 상품은 판매중지할 수 없다")
    void rejectStopSaleByAdminOfPendingProduct() {
        withSaleStartInputs();

        assertRejected(product::stopSaleByAdmin, ProductErrorCode.SALE_NOT_STARTED);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SALE_PENDING);
        assertThat(product.isStoppedByAdmin()).isFalse();
    }

    @Test
    @DisplayName("SALE-11 관리자가 판매중지한 상품은 판매자가 다시 판매를 시작할 수 없다")
    void rejectRestartSaleOfAdminStoppedProduct() {
        withSaleStarted();
        product.stopSaleByAdmin();

        assertRejected(() -> product.startSale(SELLER_ID, top, clothing.type()),
            ProductErrorCode.SALE_STOPPED_BY_ADMIN);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.STOPPED);
    }

    @Test
    @DisplayName("OPT-08 관리자가 판매중지한 상품을 다른 판매자가 다시 열면 409보다 403이 먼저다")
    void rejectRestartSaleOfAdminStoppedProductFromOtherSeller() {
        withSaleStarted();
        product.stopSaleByAdmin();

        assertRejected(() -> product.startSale(OTHER_SELLER_ID, top, clothing.type()),
            ProductErrorCode.NOT_PRODUCT_OWNER);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.STOPPED);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("productsToStartByAdmin")
    @DisplayName("SALE-03 · SALE-11 관리자는 판매대기 · 판매중지 상품을 판매 시작 조건 검사 뒤 판매중으로 바꾸고 관리자 판매중지 표시를 끈다")
    void startSaleByAdmin(Consumer<ProductSaleStopTest> given) {
        given.accept(this);

        boolean started = product.startSaleByAdmin(top, clothing.type());

        assertThat(started).isTrue();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
        assertThat(product.isStoppedByAdmin()).isFalse();
    }

    @Test
    @DisplayName("관리자가 판매중 상품에 판매 시작을 다시 보내면 아무것도 바꾸지 않는다 (재시도 대비)")
    void startSaleByAdminAgainChangesNothing() {
        withSaleStarted();

        boolean started = product.startSaleByAdmin(top, clothing.type());

        assertThat(started).isFalse();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("SALE-03 관리자도 판매 시작 조건을 채우지 못한 판매대기 상품은 판매를 시작할 수 없다")
    void rejectStartSaleByAdminWithoutSaleStartConditions() {
        // 옵션만 넣고 실측을 넣지 않아 판매 시작 조건을 채우지 못한다
        product.addOptions(addOptions(List.of(option(clothing.navy(), clothing.size110()))), clothing.type());

        assertRejected(() -> product.startSaleByAdmin(top, clothing.type()),
            ProductErrorCode.SALE_START_CONDITION_NOT_MET);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SALE_PENDING);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callsWithNull")
    @DisplayName("판매 중지와 관리자의 판매 시작에 판매자 · 카테고리 · 상품 유형이 null이면 거절한다")
    void rejectCallWithNull(ThrowingCallable call) {
        assertThatThrownBy(call)
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("필수입니다");
    }

    static Stream<Named<Consumer<ProductSaleStopTest>>> stoppedProducts() {
        return Stream.of(
            Named.of("판매자가 판매중지한 상품", test -> {
                test.withSaleStarted();
                test.product.stopSale(SELLER_ID);
            }),
            Named.of("관리자가 판매중지한 상품", test -> {
                test.withSaleStarted();
                test.product.stopSaleByAdmin();
            }));
    }

    static Stream<Named<Consumer<ProductSaleStopTest>>> productsBeforeStop() {
        return Stream.of(
            Named.of("판매대기", ProductSaleStopTest::withSaleStartInputs),
            Named.of("판매중", ProductSaleStopTest::withSaleStarted));
    }

    static Stream<Named<Consumer<ProductSaleStopTest>>> productsToStartByAdmin() {
        return Stream.concat(Stream.of(Named.of("판매대기", ProductSaleStopTest::withSaleStartInputs)), stoppedProducts());
    }

    // 110 사이즈만으로 판매를 시작하고 판매중지한 뒤, 실측이 없는 120 사이즈 옵션을 더하고 다시 여는 호출을 돌려준다
    static Stream<Named<Function<ProductSaleStopTest, ThrowingCallable>>> restartsWithoutSaleStartConditions() {
        return Stream.of(
            Named.of("판매자가 판매중지한 상품을 판매자가 다시 열 때", test -> {
                test.withSaleStartedOnlyIn110();
                test.product.stopSale(SELLER_ID);
                test.addNavy120WithoutSizeSpec();
                return () -> test.product.startSale(SELLER_ID, test.top, test.clothing.type());
            }),
            Named.of("관리자가 판매중지한 상품을 관리자가 다시 열 때", test -> {
                test.withSaleStartedOnlyIn110();
                test.product.stopSaleByAdmin();
                test.addNavy120WithoutSizeSpec();
                return () -> test.product.startSaleByAdmin(test.top, test.clothing.type());
            }));
    }

    static Stream<Named<ThrowingCallable>> callsWithNull() {
        ClothingOptions clothing = clothingOptions();
        Category top = CategoryFixture.top(CategoryFixture.clothing(), clothing.type());
        ProductType type = clothing.type();
        return Stream.of(
            Named.of("판매 중지의 판매자", () -> productA(clothing).stopSale(null)),
            Named.of("관리자 판매 시작의 카테고리", () -> productA(clothing).startSaleByAdmin(null, type)),
            Named.of("관리자 판매 시작의 상품 유형", () -> productA(clothing).startSaleByAdmin(top, null)));
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

    // 상품 A에 네이비 110 옵션과 110 사이즈의 필수 실측 · 권장 키만 넣고 판매를 시작한다
    private void withSaleStartedOnlyIn110() {
        product.addOptions(addOptions(List.of(option(clothing.navy(), clothing.size110()))), clothing.type());
        product.addSizeSpecs(addSizeSpecs(List.of(
            spec(clothing.size110(), 105, 115, top110Measurements()))), top, clothing.type());
        product.startSale(SELLER_ID, top, clothing.type());
    }

    // 실측을 넣지 않은 120 사이즈의 네이비 옵션을 더한다. 판매 시작 조건을 채우지 못하게 된다
    private void addNavy120WithoutSizeSpec() {
        product.addOptions(addOptions(List.of(option(clothing.navy(), clothing.size120()))), clothing.type());
    }
}
