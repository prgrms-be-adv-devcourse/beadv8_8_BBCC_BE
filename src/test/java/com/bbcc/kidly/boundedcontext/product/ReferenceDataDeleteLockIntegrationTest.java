package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ReferenceDataLockSaver.infoLink;
import static com.bbcc.kidly.boundedcontext.product.TransactionRace.assertWaitedThenRejected;

import com.bbcc.kidly.boundedcontext.product.ClothingTopSaver.SavedClothingTop;
import com.bbcc.kidly.boundedcontext.product.app.type.AttributeService;
import com.bbcc.kidly.boundedcontext.product.app.type.CategoryService;
import com.bbcc.kidly.boundedcontext.product.app.type.MeasurementItemService;
import com.bbcc.kidly.boundedcontext.product.app.type.ProductTypeService;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.CategoryChangeRequest;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.CategoryCreateRequest;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.ProductTypeChangeRequest;
import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItemErrorCode;
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
 * 관리자의 기준 데이터 삭제와 연결(카테고리 · 유형 생성 · 변경)이 동시에 겹칠 때 뒤의 요청이 앞의 커밋을 기다리는지
 * {@link TransactionRace}로 실제 스레드 두 개를 겹쳐 확인한다. 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
class ReferenceDataDeleteLockIntegrationTest {

    @Autowired
    private ClothingTopSaver clothingTopSaver;

    @Autowired
    private ReferenceDataLockSaver saver;

    @Autowired
    private TransactionRace race;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ProductTypeService productTypeService;

    @Autowired
    private AttributeService attributeService;

    @Autowired
    private MeasurementItemService measurementItemService;

    @Test
    @DisplayName("TYPE-10 상품 유형 삭제가 커밋되기 전이면 그 유형으로 카테고리를 만드는 요청은 기다렸다가 400으로 거절된다")
    void linkWaitsForDelete() throws Exception {
        SavedClothingTop saved = clothingTopSaver.save();
        ProductType otherType = saver.saveOtherType(saved);

        TransactionRace.Outcome outcome = race.run(
            () -> productTypeService.delete(otherType.getId()),
            () -> categoryService.create(new CategoryCreateRequest(
                "모자", saved.clothingCategory().getId(), otherType.getId(), List.of())));

        assertWaitedThenRejected(outcome, CategoryErrorCode.DELETED_REFERENCE);
    }

    /**
     * 연결을 만드는 앞의 작업과 그 대상을 지우는 뒤의 작업이다.
     */
    record LinkAndDelete(Runnable link, Runnable delete) {
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("linksAndDeletes")
    @DisplayName("TYPE-10 연결이 커밋되기 전이면 항목 · 상품 유형 · 중간 카테고리 · 실측 항목 삭제는 연결을 기다렸다가 409로 거절된다")
    void deleteWaitsForLink(
        BiFunction<ReferenceDataDeleteLockIntegrationTest, SavedClothingTop, LinkAndDelete> scenario,
        ErrorCode errorCode) throws Exception {
        SavedClothingTop saved = clothingTopSaver.save();
        LinkAndDelete linkAndDelete = scenario.apply(this, saved);

        TransactionRace.Outcome outcome = race.run(linkAndDelete.link(), linkAndDelete.delete());

        assertWaitedThenRejected(outcome, errorCode);
    }

    static Stream<Arguments> linksAndDeletes() {
        return Stream.of(
            scenario("유형에 연결하는 항목", (test, saved) -> {
                Attribute season = test.saver.saveSeason();
                ProductTypeChangeRequest request = test.saver.linksPlus(saved, infoLink(season, false));
                return new LinkAndDelete(
                    () -> test.productTypeService.change(saved.clothing().type().getId(), request),
                    () -> test.attributeService.delete(season.getId()));
            }, AttributeErrorCode.ATTRIBUTE_IN_USE),
            scenario("카테고리가 가리키는 상품 유형", (test, saved) -> {
                ProductType otherType = test.saver.saveOtherType(saved);
                return new LinkAndDelete(
                    () -> test.categoryService.create(new CategoryCreateRequest(
                        "모자", saved.clothingCategory().getId(), otherType.getId(), List.of())),
                    () -> test.productTypeService.delete(otherType.getId()));
            }, ProductTypeErrorCode.PRODUCT_TYPE_IN_USE),
            scenario("자식을 만드는 중간 카테고리", (test, saved) -> {
                Category goods = test.saver.save(Category.createMiddle("잡화", null));
                return new LinkAndDelete(
                    () -> test.categoryService.create(
                        new CategoryCreateRequest("모자", goods.getId(), null, List.of())),
                    () -> test.categoryService.delete(goods.getId()));
            }, CategoryErrorCode.CATEGORY_IN_USE),
            scenario("카테고리가 필수로 연결하는 실측 항목", (test, saved) -> {
                MeasurementItem headCircumference =
                    test.saver.save(CategoryFixture.headCircumference());
                return new LinkAndDelete(
                    () -> test.categoryService.change(saved.top().getId(), new CategoryChangeRequest(
                        saved.clothing().type().getId(), List.of(headCircumference.getId()))),
                    () -> test.measurementItemService.delete(headCircumference.getId()));
            }, MeasurementItemErrorCode.MEASUREMENT_ITEM_IN_USE));
    }

    private static Arguments scenario(String name,
        BiFunction<ReferenceDataDeleteLockIntegrationTest, SavedClothingTop, LinkAndDelete> scenario,
        ErrorCode errorCode) {
        return Arguments.of(Named.of(name, scenario), errorCode);
    }
}
