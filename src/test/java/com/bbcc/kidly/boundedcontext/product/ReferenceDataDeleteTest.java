package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItemErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeErrorCode;
import com.bbcc.kidly.global.entity.BaseSoftDeleteEntity;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.ErrorCode;
import java.time.LocalDateTime;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

class ReferenceDataDeleteTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 18, 10, 0);

    /**
     * 지울 기준 데이터와, 그것을 「쓰이는 중인지」와 함께 지우는 방법이다.
     */
    record Deletion<T extends BaseSoftDeleteEntity>(T target, BiConsumer<T, Boolean> delete) {

        void run(boolean inUse) {
            delete.accept(target, inUse);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("deletionsInUse")
    @DisplayName("TYPE-10 쓰이는 중인 항목 · 상품 유형 · 중간 카테고리 · 실측 항목은 409로 거절하고 지우지 않는다")
    void rejectDeletingInUse(Deletion<?> deletion, ErrorCode errorCode) {
        assertThatThrownBy(() -> deletion.run(true))
            .isInstanceOfSatisfying(BusinessException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo(errorCode);
                assertThat(e.getErrorCode().status()).isEqualTo(HttpStatus.CONFLICT);
            });
        assertThat(deletion.target().isDeleted()).isFalse();
    }

    static Stream<Arguments> deletionsInUse() {
        return Stream.of(
            Arguments.of(named("유형에 연결된 항목", new Deletion<>(ProductTypeFixture.color(),
                (Attribute target, Boolean inUse) -> target.delete(NOW, inUse))),
                AttributeErrorCode.ATTRIBUTE_IN_USE),
            Arguments.of(named("카테고리가 가리키는 상품 유형",
                new Deletion<>(ProductType.define("의류", ProductTypeFixture.clothingLinks()),
                    (ProductType target, Boolean inUse) -> target.delete(NOW, inUse))),
                ProductTypeErrorCode.PRODUCT_TYPE_IN_USE),
            Arguments.of(named("자식이 있는 중간 카테고리", new Deletion<>(CategoryFixture.clothing(),
                (Category target, Boolean inUse) -> target.delete(NOW, inUse))),
                CategoryErrorCode.CATEGORY_IN_USE),
            Arguments.of(named("카테고리가 필수로 연결한 실측 항목", new Deletion<>(CategoryFixture.totalLength(),
                (MeasurementItem target, Boolean inUse) -> target.delete(NOW, inUse))),
                MeasurementItemErrorCode.MEASUREMENT_ITEM_IN_USE));
    }

    private static Named<Deletion<?>> named(String name, Deletion<?> deletion) {
        return Named.of(name, deletion);
    }
}
