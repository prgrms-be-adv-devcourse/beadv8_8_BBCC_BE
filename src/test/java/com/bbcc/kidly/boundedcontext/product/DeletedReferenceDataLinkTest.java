package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.requiredOption;
import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.restrictedOption;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeErrorCode;
import com.bbcc.kidly.global.entity.BaseSoftDeleteEntity;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

/**
 * 관리자가 카테고리 · 상품 유형을 만들거나 바꿀 때 지운 기준 데이터를 새로 연결하지 못하는지 확인한다.
 */
class DeletedReferenceDataLinkTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 18, 10, 0);

    @ParameterizedTest(name = "{0}")
    @MethodSource("linkingDeleted")
    @DisplayName("TYPE-10 지운 부모 · 상품 유형 · 실측 항목 · 항목 · 허용 값을 새로 연결하면 400으로 거절한다")
    void rejectLinkingDeleted(ThrowingCallable link, ErrorCode errorCode) {
        assertThatThrownBy(link)
            .isInstanceOfSatisfying(BusinessException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo(errorCode);
                assertThat(e.getErrorCode().status()).isEqualTo(HttpStatus.BAD_REQUEST);
            });
    }

    static Stream<Arguments> linkingDeleted() {
        Category deletedParent = deleted(CategoryFixture.clothing());
        ProductType deletedType = deleted(ProductType.define("모자", ProductTypeFixture.clothingLinks()));
        MeasurementItem deletedItem = deleted(CategoryFixture.totalLength());
        Category top = CategoryFixture.top(CategoryFixture.clothing(),
            ProductType.define("의류", ProductTypeFixture.clothingLinks()));
        Attribute deletedAttribute = deleted(ProductTypeFixture.color());
        Attribute color = ProductTypeFixture.color();
        AttributeValue navy = AttributeValue.create(color, "네이비", "#2f3e5c");
        AttributeValue deletedRed = deleted(AttributeValue.create(color, "레드", "#d64545"));
        ProductType navyOnly = ProductType.define("의류", List.of(restrictedOption(color, List.of(navy))));
        return Stream.of(
            linking("지운 부모 아래 중간 카테고리", () -> Category.createMiddle("상의", deletedParent),
                CategoryErrorCode.DELETED_REFERENCE),
            linking("지운 부모 아래 말단 카테고리", () -> Category.createLeaf(new Category.LeafInfo(
                "상의", deletedParent, top.getProductType(), List.of())), CategoryErrorCode.DELETED_REFERENCE),
            linking("지운 상품 유형으로 말단 카테고리", () -> Category.createLeaf(new Category.LeafInfo(
                "모자", null, deletedType, List.of())), CategoryErrorCode.DELETED_REFERENCE),
            linking("지운 실측 항목으로 말단 카테고리", () -> Category.createLeaf(new Category.LeafInfo(
                "상의", null, top.getProductType(), List.of(deletedItem))), CategoryErrorCode.DELETED_REFERENCE),
            linking("카테고리를 지운 상품 유형으로 바꾸기", () -> top.change(deletedType, List.of(), false),
                CategoryErrorCode.DELETED_REFERENCE),
            linking("카테고리에 지운 실측 항목 넣기",
                () -> top.change(top.getProductType(), List.of(deletedItem), false),
                CategoryErrorCode.DELETED_REFERENCE),
            linking("지운 항목을 유형에 연결",
                () -> ProductType.define("의류", List.of(requiredOption(deletedAttribute))),
                ProductTypeErrorCode.DELETED_REFERENCE),
            linking("지운 값을 허용 값으로 유형 만들기",
                () -> ProductType.define("의류", List.of(restrictedOption(color, List.of(deletedRed)))),
                ProductTypeErrorCode.DELETED_REFERENCE),
            linking("유형에 지운 값을 허용 값으로 더하기",
                () -> navyOnly.change(List.of(restrictedOption(color, List.of(navy, deletedRed))), false),
                ProductTypeErrorCode.DELETED_REFERENCE));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("keepingDeletedAllowedValue")
    @DisplayName("TYPE-07 이미 허용 값으로 연결된 값이 나중에 지워졌으면, 상품이 있어도 그대로 두거나 뺄 수 있다")
    void acceptKeepingOrDroppingDeletedAllowedValue(boolean keep) {
        Attribute color = ProductTypeFixture.color();
        AttributeValue navy = AttributeValue.create(color, "네이비", "#2f3e5c");
        AttributeValue red = AttributeValue.create(color, "레드", "#d64545");
        ProductType type = ProductType.define("의류", List.of(restrictedOption(color, List.of(navy, red))));
        red.delete(NOW);
        List<AttributeValue> allowed = keep ? List.of(navy, red) : List.of(navy);

        assertThatCode(() -> type.change(List.of(restrictedOption(color, allowed)), true))
            .doesNotThrowAnyException();
    }

    static Stream<Named<Boolean>> keepingDeletedAllowedValue() {
        return Stream.of(Named.of("그대로 두기", true), Named.of("빼기", false));
    }

    private static Arguments linking(String name, ThrowingCallable link, ErrorCode errorCode) {
        return Arguments.of(Named.of(name, link), errorCode);
    }

    private static <T extends BaseSoftDeleteEntity> T deleted(T entity) {
        entity.delete(NOW);
        return entity;
    }
}
