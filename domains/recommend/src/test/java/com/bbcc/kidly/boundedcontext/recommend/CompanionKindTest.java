package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionKind.ACCESSORY;
import static com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionKind.ONE_PIECE;
import static com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionKind.OUTER;
import static com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionKind.PANTS;
import static com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionKind.SKIRT;
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
            Arguments.of(List.of("의류", "치마"), SKIRT),
            Arguments.of(List.of("의류", "원피스"), ONE_PIECE),
            Arguments.of(List.of("의류", "아우터"), OUTER),
            Arguments.of(List.of("잡화", "모자"), ACCESSORY),
            Arguments.of(List.of("잡화", "양말"), ACCESSORY),
            Arguments.of(List.of("잡화"), ACCESSORY));
    }

    static Stream<Arguments> kindsByVariantCategoryNames() {
        return Stream.of(
            Arguments.of(List.of("의류", "바지"), PANTS),
            Arguments.of(List.of("의류", "하의"), PANTS),
            Arguments.of(List.of("의류", " 상의 "), TOP),
            Arguments.of(List.of(" 잡화", "모자"), ACCESSORY));
    }

    @ParameterizedTest
    @MethodSource("kindsByVariantCategoryNames")
    @DisplayName("PAIR-07 하의와 바지는 같은 종류(바지)이고, 카테고리 이름 앞뒤의 공백은 무시한다")
    void kindFromVariantCategoryNames(List<String> categoryPathNames, CompanionKind expected) {
        assertThat(CompanionKind.of(categoryPathNames)).contains(expected);
    }

    @ParameterizedTest
    @MethodSource("kindsByCategoryPath")
    @DisplayName("PAIR-01 의류는 말단 카테고리 이름으로 종류를 정하고, 대분류가 잡화면 말단과 상관없이 잡화다")
    void kindFromCategoryPath(List<String> categoryPathNames, CompanionKind expected) {
        assertThat(CompanionKind.of(categoryPathNames)).contains(expected);
    }

    static Stream<List<String>> unknownCategoryPaths() {
        return Stream.of(List.of("의류", "내의"), List.of("가구", "의자"), List.of());
    }

    @ParameterizedTest
    @MethodSource("unknownCategoryPaths")
    @DisplayName("PAIR-01 표에 없는 카테고리이거나 카테고리 경로가 없는 상품은 종류가 없어 짝을 찾지 않는다")
    void noKindForUnknownCategoryPath(List<String> categoryPathNames) {
        assertThat(CompanionKind.of(categoryPathNames)).isEmpty();
    }
}
