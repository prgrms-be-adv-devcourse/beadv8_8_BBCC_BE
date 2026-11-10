package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionKind.ACCESSORY;
import static com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionKind.BOTTOM;
import static com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionKind.ONE_PIECE;
import static com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionKind.OUTER;
import static com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionKind.TOP;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionKind;
import com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionPairs;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CompanionPairsTest {

    static Stream<Arguments> pairTable() {
        return Stream.of(
            Arguments.of(TOP, List.of(BOTTOM, OUTER, ACCESSORY)),
            Arguments.of(BOTTOM, List.of(TOP, ONE_PIECE, OUTER, ACCESSORY)),
            Arguments.of(ONE_PIECE, List.of(BOTTOM, OUTER, ACCESSORY)),
            Arguments.of(OUTER, List.of(BOTTOM, TOP, ONE_PIECE, ACCESSORY)),
            Arguments.of(ACCESSORY, List.of(TOP, BOTTOM, ONE_PIECE, OUTER)));
    }

    @ParameterizedTest
    @MethodSource("pairTable")
    @DisplayName("PAIR-01 PAIR-02 보고 있는 상품의 종류마다 짝 종류는 표와 같고, 표에 적힌 우선순위 순서다")
    void pairsFollowTable(CompanionKind viewed, List<CompanionKind> expected) {
        assertThat(CompanionPairs.of(viewed)).containsExactlyElementsOf(expected);
    }

    @Test
    @DisplayName("PAIR-01 어떤 종류도 자기 자신과 짝이 아니고, 상의와 원피스는 서로 짝이 아니다")
    void sameKindAndTopWithOnePieceAreNotPairs() {
        for (CompanionKind kind : CompanionKind.values()) {
            assertThat(CompanionPairs.of(kind)).doesNotContain(kind);
        }
        assertThat(CompanionPairs.of(TOP)).doesNotContain(ONE_PIECE);
        assertThat(CompanionPairs.of(ONE_PIECE)).doesNotContain(TOP);
    }

    @Test
    @DisplayName("PAIR-03 짝은 양쪽으로 성립하고, 아우터와 잡화는 자기를 뺀 나머지 종류 전부와 짝이다")
    void pairsAreSymmetric() {
        for (CompanionKind a : CompanionKind.values()) {
            for (CompanionKind b : CompanionPairs.of(a)) {
                assertThat(CompanionPairs.of(b)).as("%s의 짝에 %s가 있으면 %s의 짝에도 %s가 있다", a, b, b, a)
                    .contains(a);
            }
        }
        assertThat(CompanionPairs.of(OUTER)).containsExactlyInAnyOrder(TOP, BOTTOM, ONE_PIECE, ACCESSORY);
        assertThat(CompanionPairs.of(ACCESSORY)).containsExactlyInAnyOrder(TOP, BOTTOM, ONE_PIECE, OUTER);
    }
}
