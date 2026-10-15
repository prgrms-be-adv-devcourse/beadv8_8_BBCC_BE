package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.product.domain.product.ProductErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.product.SkuCodes;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SkuCodesTest {

    @Test
    @DisplayName("OPT-07 판매자가 이미 쓰는 SKU 코드를 넣으면 409로 거절하고, 겹친 코드를 요청 순서대로 모두 알려 준다")
    void rejectCodesInUse() {
        assertDuplicated(() -> SkuCodes.validateUnique(
                List.of("A-NV-110", "A-NV-120", "A-NV-130"), Set.of("A-NV-130", "A-NV-110")),
            "A-NV-110, A-NV-130");
    }

    @Test
    @DisplayName("OPT-07 새 옵션끼리 같은 SKU 코드를 넣으면 409로 거절하고, 겹친 코드는 여러 번 나와도 한 번만 알려 준다")
    void rejectCodesRepeatedInRequest() {
        assertDuplicated(() -> SkuCodes.validateUnique(
                List.of("A-NV-110", "A-NV-120", "A-NV-110", "A-NV-110"), Set.of()),
            "A-NV-110");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("uniqueCodes")
    @DisplayName("OPT-07 SKU 코드가 없는 옵션은 여러 개여도 검사하지 않고, 겹치지 않는 코드는 통과한다")
    void acceptUniqueOrMissingCodes(List<String> newCodes, Set<String> codesInUse) {
        assertThatCode(() -> SkuCodes.validateUnique(newCodes, codesInUse)).doesNotThrowAnyException();
    }

    static Stream<Arguments> uniqueCodes() {
        return Stream.of(
            Arguments.of(Named.of("코드 없는 옵션 여러 개", Arrays.asList(null, null)), Set.of("A-NV-110")),
            Arguments.of(Named.of("이미 쓰는 코드와 다른 코드", List.of("A-NV-120", "A-NV-130")), Set.of("A-NV-110")),
            Arguments.of(Named.of("코드 없는 옵션과 새 코드", Arrays.asList(null, "A-NV-120", null)), Set.of()),
            Arguments.of(Named.of("대소문자만 다른 코드", List.of("a-nv-110")), Set.of("A-NV-110")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nullLists")
    @DisplayName("새 코드 목록이나 이미 쓰는 코드 목록이 null이면 거절한다")
    void rejectNullLists(ThrowingCallable validate) {
        assertThatThrownBy(validate)
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("필수입니다");
    }

    static Stream<Named<ThrowingCallable>> nullLists() {
        return Stream.of(
            Named.of("새 코드 목록", () -> SkuCodes.validateUnique(null, Set.of())),
            Named.of("이미 쓰는 코드 목록", () -> SkuCodes.validateUnique(List.of("A-NV-110"), null)));
    }

    // 409 오류 코드로 거절하고 detail 끝에 겹친 코드를 적는지 확인한다
    private static void assertDuplicated(ThrowingCallable call, String codes) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.DUPLICATE_SKU_CODE);
            assertThat(e.getDetail()).isEqualTo(
                "같은 판매자의 다른 옵션과 겹치지 않게 SKU 코드를 넣어 주세요. 겹친 코드는 " + codes + "입니다.");
        });
    }
}
