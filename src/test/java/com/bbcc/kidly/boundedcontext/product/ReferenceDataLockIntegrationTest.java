package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.ClothingTopSaver.SavedClothingTop;
import com.bbcc.kidly.boundedcontext.product.app.product.ProductRegistrationService;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductRegisterRequest;
import com.bbcc.kidly.boundedcontext.product.app.type.CategoryService;
import com.bbcc.kidly.boundedcontext.product.app.type.ProductTypeService;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.CategoryChangeRequest;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.ProductTypeChangeRequest;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.ProductTypeCreateRequest;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeInputType;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeAllowedValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeAttribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeErrorCode;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 관리자의 카테고리 · 유형 설정 변경과 판매자 상품 등록이 동시에 겹칠 때 뒤의 요청이 앞의 커밋을 기다리는지
 * {@link TransactionRace}로 실제 스레드 두 개를 겹쳐 확인한다. 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
class ReferenceDataLockIntegrationTest {

    @Autowired
    private ClothingTopSaver clothingTopSaver;

    @Autowired
    private TransactionRace race;

    @Autowired
    private ProductRegistrationService productRegistrationService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ProductTypeService productTypeService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("TYPE-07 첫 상품 등록이 커밋되기 전이면 카테고리 유형 변경은 등록을 기다렸다가 409로 거절된다")
    void categoryChangeWaitsForFirstRegistration() throws Exception {
        SavedClothingTop saved = clothingTopSaver.save();
        ProductType otherType = savedOtherType(saved);

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
        ProductType otherType = savedOtherType(saved);

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
        ProductTypeChangeRequest addRequiredInfo = linksWithRequiredSeason(saved);

        TransactionRace.Outcome outcome = race.run(
            () -> productRegistrationService.register(ProductFixture.SELLER_ID, registerRequest(saved)),
            () -> productTypeService.change(saved.clothing().type().getId(), addRequiredInfo));

        assertThat(outcome.waited()).isTrue();
        assertThat(outcome.error()).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ProductTypeErrorCode.CHANGE_BREAKS_PRODUCTS));
    }

    // 상의 카테고리의 의류 유형과 다른 유형을 저장한다. 색상만 필수 옵션 항목이라 의류 상품의 정보 값 · 사이즈와 맞지 않는다
    private ProductType savedOtherType(SavedClothingTop saved) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Attribute color = entityManager.find(Attribute.class, saved.clothing().navy().getAttribute().getId());
            ProductType otherType = ProductType.define("다른 유형", List.of(ProductTypeFixture.requiredOption(color)));
            entityManager.persist(otherType);
            return otherType;
        });
    }

    // 의류 유형의 지금 연결을 그대로 두고 필수 정보 항목 「계절」을 끝에 더하는 변경 요청이다. 계절 항목은 여기서 저장한다
    private ProductTypeChangeRequest linksWithRequiredSeason(SavedClothingTop saved) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Attribute season = Attribute.create("계절", "SEASON", AttributeInputType.LIST);
            entityManager.persist(season);
            ProductType type = entityManager.find(ProductType.class, saved.clothing().type().getId());
            List<ProductTypeCreateRequest.Link> links = new ArrayList<>();
            for (ProductTypeAttribute link : type.getAttributes()) {
                List<Long> allowedValueIds = new ArrayList<>();
                for (ProductTypeAllowedValue allowed : type.getAllowedValues()) {
                    if (allowed.getAttributeValue().getAttribute() == link.getAttribute()) {
                        allowedValueIds.add(allowed.getAttributeValue().getId());
                    }
                }
                links.add(new ProductTypeCreateRequest.Link(link.getAttribute().getId(), link.getRole().name(),
                    link.isRequired(), link.isAllValuesAllowed(), link.isSizeRole(), link.isPriceAffecting(),
                    allowedValueIds));
            }
            links.add(new ProductTypeCreateRequest.Link(season.getId(), "INFO", true, true, false, false, List.of()));
            return new ProductTypeChangeRequest(links);
        });
    }

    // 의류 > 상의에 네이비 · 110 옵션 하나와 110 실측을 채운 판매대기 상품 등록 요청이다
    private static ProductRegisterRequest registerRequest(SavedClothingTop saved) {
        Long size110 = saved.clothing().size110().getId();
        return new ProductRegisterRequest("데일리 맨투맨", "BBCC", saved.top().getId(), null, null, List.of(),
            List.of(new ProductRegisterRequest.AttributeInput(
                saved.clothing().mainMaterial().getId(), List.of(saved.clothing().cotton().getId()), null)),
            List.of(new ProductRegisterRequest.OptionInput(
                List.of(saved.clothing().navy().getId(), size110), 10, null)),
            List.of(new ProductRegisterRequest.PriceInput(List.of(), 25_000L)),
            List.of(new ProductRegisterRequest.SizeSpecInput(size110, 105, 115, Map.of(
                "TOTAL_LENGTH", BigDecimal.valueOf(42), "SHOULDER_WIDTH", BigDecimal.valueOf(30),
                "CHEST_WIDTH", BigDecimal.valueOf(34), "SLEEVE_LENGTH", BigDecimal.valueOf(38)))));
    }
}
