package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ReferenceDataLockSaver.infoLink;
import static com.bbcc.kidly.boundedcontext.product.ReferenceDataLockSaver.registerRequest;
import static com.bbcc.kidly.boundedcontext.product.TransactionRace.assertWaitedThenRejected;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.ClothingTopSaver.SavedClothingTop;
import com.bbcc.kidly.boundedcontext.product.app.product.ProductRegistrationService;
import com.bbcc.kidly.boundedcontext.product.app.product.ProductUpdateService;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductRegisterRequest;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductRegisterResult;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductUpdateRequest;
import com.bbcc.kidly.boundedcontext.product.app.type.CategoryService;
import com.bbcc.kidly.boundedcontext.product.app.type.ProductTypeService;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.CategoryChangeRequest;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.ProductTypeChangeRequest;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.ProductTypeCreateRequest;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeErrorCode;
import com.bbcc.kidly.global.error.ErrorCode;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 관리자의 카테고리 · 유형 설정 변경과 판매자의 상품 등록 · 카테고리 이동이 동시에 겹칠 때 뒤의 요청이 앞의 커밋을
 * 기다리는지 {@link TransactionRace}로 실제 스레드 두 개를 겹쳐 확인한다. 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
class ProductRegistrationLockIntegrationTest {

    @Autowired
    private ClothingTopSaver clothingTopSaver;

    @Autowired
    private ReferenceDataLockSaver saver;

    @Autowired
    private TransactionRace race;

    @Autowired
    private ProductRegistrationService productRegistrationService;

    @Autowired
    private ProductUpdateService productUpdateService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ProductTypeService productTypeService;

    /**
     * 앞의 작업과 그 커밋을 기다리는 뒤의 작업이다.
     */
    record Overlap(Runnable first, Runnable second) {
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("changesDuringFirstRegistration")
    @DisplayName("TYPE-07 첫 상품 등록이 커밋되기 전이면 카테고리 유형 변경 · 유형 필수 정보 항목 추가는 "
        + "등록을 기다렸다가 409로 거절된다")
    void changeWaitsForFirstRegistration(
        BiFunction<ProductRegistrationLockIntegrationTest, SavedClothingTop, Overlap> scenario,
        ErrorCode errorCode) throws Exception {
        SavedClothingTop saved = clothingTopSaver.save();
        Overlap overlap = scenario.apply(this, saved);

        TransactionRace.Outcome outcome = race.run(overlap.first(), overlap.second());

        assertWaitedThenRejected(outcome, errorCode);
    }

    static Stream<Arguments> changesDuringFirstRegistration() {
        return Stream.of(
            scenario("카테고리 유형 변경", (test, saved) -> {
                ProductType otherType = test.saver.saveOtherType(saved);
                return new Overlap(
                    () -> test.productRegistrationService.register(ProductFixture.SELLER_ID, registerRequest(saved)),
                    () -> test.categoryService.change(
                        saved.top().getId(), new CategoryChangeRequest(otherType.getId(), List.of())));
            }, CategoryErrorCode.CHANGE_BREAKS_PRODUCTS),
            scenario("유형에 필수 정보 항목 추가", (test, saved) -> {
                ProductTypeChangeRequest addRequiredInfo =
                    test.saver.linksPlus(saved.clothing().type().getId(), infoLink(test.saver.saveSeason(), true));
                return new Overlap(
                    () -> test.productRegistrationService.register(ProductFixture.SELLER_ID, registerRequest(saved)),
                    () -> test.productTypeService.change(saved.clothing().type().getId(), addRequiredInfo));
            }, ProductTypeErrorCode.CHANGE_BREAKS_PRODUCTS));
    }

    private static Arguments scenario(String name,
        BiFunction<ProductRegistrationLockIntegrationTest, SavedClothingTop, Overlap> scenario,
        ErrorCode errorCode) {
        return Arguments.of(Named.of(name, scenario), errorCode);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("registrationsDuringCategoryChange")
    @DisplayName("TYPE-07 카테고리 유형 변경이 커밋되기 전이면 상품 등록 · 상품을 그 카테고리로 옮기는 수정은 "
        + "변경을 기다렸다가 바뀐 유형으로 검사해 400으로 거절된다")
    void registrationWaitsForCategoryChange(
        BiFunction<ProductRegistrationLockIntegrationTest, SavedClothingTop, Overlap> scenario) throws Exception {
        SavedClothingTop saved = clothingTopSaver.save();
        Overlap overlap = scenario.apply(this, saved);

        TransactionRace.Outcome outcome = race.run(overlap.first(), overlap.second());

        assertWaitedThenRejected(outcome, ProductErrorCode.NOT_INFO_ATTRIBUTE);
    }

    static Stream<Named<BiFunction<ProductRegistrationLockIntegrationTest, SavedClothingTop, Overlap>>>
        registrationsDuringCategoryChange() {
        return Stream.of(
            Named.of("상품 등록", (test, saved) -> {
                ProductType otherType = test.saver.saveOtherType(saved);
                return new Overlap(
                    () -> test.categoryService.change(
                        saved.top().getId(), new CategoryChangeRequest(otherType.getId(), List.of())),
                    () -> test.productRegistrationService.register(ProductFixture.SELLER_ID, registerRequest(saved)));
            }),
            Named.of("카테고리 이동", (test, saved) -> {
                ProductType otherType = test.saver.saveOtherType(saved);
                Category outer = test.saver.saveOuter(saved);
                ProductRegisterRequest register = registerRequest(saved);
                ProductRegisterResult product =
                    test.productRegistrationService.register(ProductFixture.SELLER_ID, register);
                return new Overlap(
                    () -> test.categoryService.change(
                        outer.getId(), new CategoryChangeRequest(otherType.getId(), List.of())),
                    () -> test.productUpdateService.update(
                        ProductFixture.SELLER_ID, product.productId(), moveRequest(product, register, outer)));
            }));
    }

    @Test
    @DisplayName("TYPE-07 유형에 필수 정보 항목을 더하는 변경이 커밋되기 전이면 상품을 그 유형의 카테고리로 옮기는 수정은 "
        + "변경을 기다렸다가 바뀐 설정으로 검사해 400으로 거절된다")
    void moveWaitsForProductTypeChange() throws Exception {
        SavedClothingTop saved = clothingTopSaver.save();
        Long copiedTypeId = productTypeService.create(new ProductTypeCreateRequest(
            "의류 사본", saver.linksOf(saved.clothing().type().getId()))).productTypeId();
        Category outer = saver.saveOuter(saved, copiedTypeId);
        ProductTypeChangeRequest addRequiredInfo = saver.linksPlus(copiedTypeId, infoLink(saver.saveSeason(), true));
        ProductRegisterRequest register = registerRequest(saved);
        ProductRegisterResult product = productRegistrationService.register(ProductFixture.SELLER_ID, register);

        TransactionRace.Outcome outcome = race.run(
            () -> productTypeService.change(copiedTypeId, addRequiredInfo),
            () -> productUpdateService.update(
                ProductFixture.SELLER_ID, product.productId(), moveRequest(product, register, outer)));

        assertWaitedThenRejected(outcome, ProductErrorCode.REQUIRED_INFO_MISSING);
    }

    // 등록한 상품의 값을 그대로 두고 카테고리만 옮기는 수정 요청이다
    private static ProductUpdateRequest moveRequest(
        ProductRegisterResult product, ProductRegisterRequest register, Category to) {
        return new ProductUpdateRequest(product.version(), register.name(), register.brand(), to.getId(), null, null,
            List.of(), register.attributes(), register.options(), register.prices(), register.sizeSpecs());
    }

    @Test
    @DisplayName("같은 카테고리에 상품을 등록하는 판매자끼리는 서로 기다리지 않는다")
    void registrationsDoNotWaitForEachOther() throws Exception {
        SavedClothingTop saved = clothingTopSaver.save();

        TransactionRace.Outcome outcome = race.run(
            () -> productRegistrationService.register(ProductFixture.SELLER_ID, registerRequest(saved)),
            () -> productRegistrationService.register(ProductFixture.SELLER_ID, registerRequest(saved)));

        assertThat(outcome.waited()).isFalse();
        assertThat(outcome.error()).isNull();
    }
}
