package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionKind.ACCESSORY;
import static com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionKind.BOTTOM;
import static com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionKind.ONE_PIECE;
import static com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionKind.OUTER;
import static com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionKind.TOP;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionKind;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CompanionKindTest {

    static Stream<Arguments> kindsByCategoryPath() {
        return Stream.of(
            Arguments.of(List.of("의류", "상의"), TOP),
            Arguments.of(List.of("의류", "하의"), BOTTOM),
            Arguments.of(List.of("의류", "원피스"), ONE_PIECE),
            Arguments.of(List.of("의류", "아우터"), OUTER),
            Arguments.of(List.of("잡화", "모자"), ACCESSORY),
            Arguments.of(List.of("잡화", "양말"), ACCESSORY),
            Arguments.of(List.of("잡화"), ACCESSORY));
    }

    @ParameterizedTest
    @MethodSource("kindsByCategoryPath")
    @DisplayName("PAIR-01 의류는 말단 카테고리 이름으로 종류를 정하고, 대분류가 잡화면 말단과 상관없이 잡화다")
    void kindFromCategoryPath(List<String> categoryPathNames, CompanionKind expected) {
        assertThat(CompanionKind.of(categoryPathNames)).contains(expected);
    }
}
