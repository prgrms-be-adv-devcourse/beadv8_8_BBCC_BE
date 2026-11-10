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
}
