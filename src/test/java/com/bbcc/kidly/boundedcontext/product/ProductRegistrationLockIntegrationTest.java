package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ReferenceDataLockSaver.infoLink;
import static com.bbcc.kidly.boundedcontext.product.ReferenceDataLockSaver.registerRequest;
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
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeErrorCode;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

    @Test
    @DisplayName("TYPE-07 첫 상품 등록이 커밋되기 전이면 카테고리 유형 변경은 등록을 기다렸다가 409로 거절된다")
    void categoryChangeWaitsForFirstRegistration() throws Exception {
        SavedClothingTop saved = clothingTopSaver.save();
        ProductType otherType = saver.saveOtherType(saved);

        TransactionRace.Outcome outcome = race.run(
            () -> productRegistrationService.register(ProductFixture.SELLER_ID, registerRequest(saved)),
            () -> categoryService.change(saved.top().getId(), new CategoryChangeRequest(otherType.getId(), List.of())));

        assertThat(outcome.waited()).isTrue();
        assertThat(outcome.error()).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(CategoryErrorCode.CHANGE_BREAKS_PRODUCTS));
    }

    @Test
    @DisplayName("TYPE-07 카테고리 유형 변경이 커밋되기 전이면 상품 등록은 변경을 기다렸다가 바뀐 유형으로 검사해 400으로 거절된다")
    void registrationWaitsForCategoryChange() throws Exception {
        SavedClothingTop saved = clothingTopSaver.save();
        ProductType otherType = saver.saveOtherType(saved);

        TransactionRace.Outcome outcome = race.run(
            () -> categoryService.change(saved.top().getId(), new CategoryChangeRequest(otherType.getId(), List.of())),
            () -> productRegistrationService.register(ProductFixture.SELLER_ID, registerRequest(saved)));

        assertThat(outcome.waited()).isTrue();
        assertThat(outcome.error()).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.NOT_INFO_ATTRIBUTE));
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

    @Test
    @DisplayName("TYPE-07 첫 상품 등록이 커밋되기 전이면 유형에 필수 정보 항목을 더하는 변경은 등록을 기다렸다가 409로 거절된다")
    void productTypeChangeWaitsForFirstRegistration() throws Exception {
        SavedClothingTop saved = clothingTopSaver.save();
        ProductTypeChangeRequest addRequiredInfo = saver.linksPlus(saved, infoLink(saver.saveSeason(), true));

        TransactionRace.Outcome outcome = race.run(
            () -> productRegistrationService.register(ProductFixture.SELLER_ID, registerRequest(saved)),
            () -> productTypeService.change(saved.clothing().type().getId(), addRequiredInfo));

        assertThat(outcome.waited()).isTrue();
        assertThat(outcome.error()).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ProductTypeErrorCode.CHANGE_BREAKS_PRODUCTS));
    }

    @Test
    @DisplayName("TYPE-07 카테고리 유형 변경이 커밋되기 전이면 상품을 그 카테고리로 옮기는 수정은 기다렸다가 바뀐 유형으로 검사해 400으로 거절된다")
    void categoryMoveWaitsForCategoryChange() throws Exception {
        SavedClothingTop saved = clothingTopSaver.save();
        ProductType otherType = saver.saveOtherType(saved);
        Category outer = saver.saveOuter(saved);
        ProductRegisterRequest register = registerRequest(saved);
        ProductRegisterResult product = productRegistrationService.register(ProductFixture.SELLER_ID, register);
        ProductUpdateRequest moveToOuter = new ProductUpdateRequest(product.version(), register.name(),
            register.brand(), outer.getId(), null, null, List.of(), register.attributes(), register.options(),
            register.prices(), register.sizeSpecs());

        TransactionRace.Outcome outcome = race.run(
            () -> categoryService.change(outer.getId(), new CategoryChangeRequest(otherType.getId(), List.of())),
            () -> productUpdateService.update(ProductFixture.SELLER_ID, product.productId(), moveToOuter));

        assertThat(outcome.waited()).isTrue();
        assertThat(outcome.error()).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.NOT_INFO_ATTRIBUTE));
    }
}
