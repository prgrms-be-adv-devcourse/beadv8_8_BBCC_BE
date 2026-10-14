package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItem;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class MeasurementItemTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("creationsWithNull")
    @DisplayName("실측 항목의 코드 · 이름 · 단위가 null이면 거절한다")
    void rejectCreationWithNull(ThrowingCallable create) {
        assertThatThrownBy(create)
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("필수입니다");
    }

    static Stream<Named<ThrowingCallable>> creationsWithNull() {
        return Stream.of(
            Named.of("이름", () -> MeasurementItem.create(null, "TOTAL_LENGTH", "cm")),
            Named.of("코드", () -> MeasurementItem.create("총장", null, "cm")),
            Named.of("단위", () -> MeasurementItem.create("총장", "TOTAL_LENGTH", null)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("creationsWithBlank")
    @DisplayName("실측 항목의 코드 · 이름 · 단위가 비어 있으면 거절한다")
    void rejectCreationWithBlank(ThrowingCallable create) {
        assertThatThrownBy(create)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("비어 있을 수 없습니다");
    }

    static Stream<Named<ThrowingCallable>> creationsWithBlank() {
        return Stream.of(
            Named.of("이름 \"\"", () -> MeasurementItem.create("", "TOTAL_LENGTH", "cm")),
            Named.of("이름 \" \"", () -> MeasurementItem.create(" ", "TOTAL_LENGTH", "cm")),
            Named.of("코드 \"\"", () -> MeasurementItem.create("총장", "", "cm")),
            Named.of("코드 \" \"", () -> MeasurementItem.create("총장", " ", "cm")),
            Named.of("단위 \"\"", () -> MeasurementItem.create("총장", "TOTAL_LENGTH", "")),
            Named.of("단위 \" \"", () -> MeasurementItem.create("총장", "TOTAL_LENGTH", " ")));
    }
}
