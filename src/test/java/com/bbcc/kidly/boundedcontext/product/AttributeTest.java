package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductTypeFixture.color;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeInputType;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AttributeTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("creationsWithNull")
    @DisplayName("항목의 이름 · 코드 · 입력 방식, 목록 값의 항목 · 이름이 null이면 거절한다")
    void rejectNull(ThrowingCallable create) {
        assertThatThrownBy(create)
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("필수입니다");
    }

    static Stream<Named<ThrowingCallable>> creationsWithNull() {
        return Stream.of(
            Named.of("항목 이름", () -> Attribute.create(null, "COLOR", AttributeInputType.PALETTE)),
            Named.of("항목 코드", () -> Attribute.create("색상", null, AttributeInputType.PALETTE)),
            Named.of("항목 입력 방식", () -> Attribute.create("색상", "COLOR", null)),
            Named.of("목록 값의 항목", () -> AttributeValue.create(null, "네이비", "#2f3e5c")),
            Named.of("목록 값 이름", () -> AttributeValue.create(color(), null, "#2f3e5c")));
    }
}
