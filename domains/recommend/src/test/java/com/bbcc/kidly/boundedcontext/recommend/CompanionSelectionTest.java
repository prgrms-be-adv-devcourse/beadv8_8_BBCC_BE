package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionAiPick;
import com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionCandidate;
import com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionPick;
import com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionSelection;
import com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionSource;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.RecommendedProduct;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class CompanionSelectionTest {

    private static final LocalDateTime SALE_STARTED_AT = LocalDateTime.of(2026, 11, 1, 10, 0);

    @Test
    @DisplayName("PAIR-06 AI가 후보에서 고른 상품을 AI가 고른 순서대로 이유와 함께 돌려주고, 출처는 AI다")
    void useAiPicksInOrderWithReasons() {
        List<CompanionCandidate> ranked = rankedCandidates(6);
        List<CompanionAiPick> aiPicks = List.of(
            new CompanionAiPick(5L, "같은 색 계열이라 함께 입기 좋아요."),
            new CompanionAiPick(2L, "가을에 겹쳐 입기 좋아요."),
            new CompanionAiPick(6L, "활동하기 편한 핏이에요."),
            new CompanionAiPick(1L, "기본 아이템이라 어디에나 어울립니다."));

        CompanionSelection selection = CompanionSelection.of(aiPicks, ranked);

        assertThat(selection.source()).isEqualTo(CompanionSource.AI);
        assertThat(selection.picks()).extracting(pick -> pick.product().productId())
            .containsExactly(5L, 2L, 6L, 1L);
        assertThat(selection.picks()).extracting(CompanionPick::reason)
            .containsExactly("같은 색 계열이라 함께 입기 좋아요.", "가을에 겹쳐 입기 좋아요.", "활동하기 편한 핏이에요.",
                "기본 아이템이라 어디에나 어울립니다.");
    }

    @Test
    @DisplayName("PAIR-06 AI가 고른 것 중 후보 밖 상품과 중복은 버리고, 모자란 자리는 규칙 순서로 이유 없이 채우며, 출처는 AI다")
    void dropInvalidAiPicksAndFillWithRuleOrder() {
        List<CompanionCandidate> ranked = rankedCandidates(6);
        List<CompanionAiPick> aiPicks = List.of(
            new CompanionAiPick(99L, "후보에 없는 상품"),
            new CompanionAiPick(3L, "색이 잘 어울려요."),
            new CompanionAiPick(3L, "중복"));

        CompanionSelection selection = CompanionSelection.of(aiPicks, ranked);

        assertThat(selection.source()).isEqualTo(CompanionSource.AI);
        assertThat(selection.picks()).extracting(pick -> pick.product().productId())
            .containsExactly(3L, 1L, 2L, 4L);
        assertThat(selection.picks()).extracting(CompanionPick::reason)
            .containsExactly("색이 잘 어울려요.", null, null, null);
    }

    @Test
    @DisplayName("PAIR-06 AI 답에 빈 항목이 섞여 있으면 그 항목만 버린다")
    void skipNullAiPicks() {
        List<CompanionCandidate> ranked = rankedCandidates(6);
        List<CompanionAiPick> aiPicks = new ArrayList<>();
        aiPicks.add(null);
        aiPicks.add(new CompanionAiPick(4L, "색이 잘 어울려요."));

        CompanionSelection selection = CompanionSelection.of(aiPicks, ranked);

        assertThat(selection.source()).isEqualTo(CompanionSource.AI);
        assertThat(selection.picks()).extracting(pick -> pick.product().productId())
            .containsExactly(4L, 1L, 2L, 3L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("PAIR-06 AI가 고른 상품의 이유가 비어 있으면 상품은 쓰고 이유는 없는 것으로 본다")
    void treatBlankReasonAsMissing(String blankReason) {
        List<CompanionCandidate> ranked = rankedCandidates(6);

        CompanionSelection selection = CompanionSelection.of(List.of(new CompanionAiPick(4L, blankReason)), ranked);

        assertThat(selection.source()).isEqualTo(CompanionSource.AI);
        assertThat(selection.picks().getFirst().product().productId()).isEqualTo(4L);
        assertThat(selection.picks().getFirst().reason()).isNull();
    }

    @Test
    @DisplayName("PAIR-06 AI에 넘기는 규칙 상위 20개 밖의 상품을 AI가 고르면 후보 밖 상품으로 보고 버린다")
    void dropAiPicksOutsideTopTwentyCandidates() {
        List<CompanionCandidate> ranked = rankedCandidates(25);

        CompanionSelection selection = CompanionSelection.of(List.of(new CompanionAiPick(21L, "후보 밖")), ranked);

        assertThat(selection.source()).isEqualTo(CompanionSource.RULE);
        assertThat(selection.picks()).extracting(pick -> pick.product().productId())
            .containsExactly(1L, 2L, 3L, 4L);
    }

    static Stream<String> displayableReasons() {
        return Stream.of("좋아요.", "좋아요", "단정한 느낌을 줍니다.", "단정한 느낌을 줍니다", "가".repeat(38) + "요.");
    }

    @ParameterizedTest
    @MethodSource("displayableReasons")
    @DisplayName("PAIR-08 40자 안에서 「요」나 「니다」로 끝나는 이유는 마침표가 있든 없든 그대로 쓴다")
    void keepPoliteReasonWithinFortyCharacters(String reason) {
        CompanionSelection selection =
            CompanionSelection.of(List.of(new CompanionAiPick(4L, reason)), rankedCandidates(6));

        assertThat(selection.picks().getFirst().reason()).isEqualTo(reason);
    }

    @Test
    @DisplayName("PAIR-08 40자를 넘는 이유는 상품은 쓰고 이유만 없는 것으로 본다")
    void dropReasonLongerThanFortyCharacters() {
        String reason = "가".repeat(39) + "요.";

        CompanionSelection selection =
            CompanionSelection.of(List.of(new CompanionAiPick(4L, reason)), rankedCandidates(6));

        assertThat(selection.source()).isEqualTo(CompanionSource.AI);
        assertThat(selection.picks().getFirst().product().productId()).isEqualTo(4L);
        assertThat(selection.picks().getFirst().reason()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"같은 색이라 함께 입기 좋다.", "겹쳐 입기 좋아…", "편안한 코디 완성"})
    @DisplayName("PAIR-08 「요」나 「니다」로 끝나지 않는 이유는 상품은 쓰고 이유만 없는 것으로 본다")
    void dropReasonNotEndingPolitely(String reason) {
        CompanionSelection selection =
            CompanionSelection.of(List.of(new CompanionAiPick(4L, reason)), rankedCandidates(6));

        assertThat(selection.picks().getFirst().product().productId()).isEqualTo(4L);
        assertThat(selection.picks().getFirst().reason()).isNull();
    }

    static Stream<List<CompanionAiPick>> noUsableAiPicks() {
        return Stream.of(null, List.of(), List.of(new CompanionAiPick(99L, "후보에 없는 상품")));
    }

    @ParameterizedTest
    @MethodSource("noUsableAiPicks")
    @DisplayName("PAIR-06 AI 결과가 없거나 AI가 고른 것이 모두 버려지면 규칙 상위 4개를 이유 없이 돌려주고, 출처는 RULE이다")
    void useRuleTopFourWhenNoUsableAiPicks(List<CompanionAiPick> aiPicks) {
        List<CompanionCandidate> ranked = rankedCandidates(6);

        CompanionSelection selection = CompanionSelection.of(aiPicks, ranked);

        assertThat(selection.source()).isEqualTo(CompanionSource.RULE);
        assertThat(selection.picks()).extracting(pick -> pick.product().productId())
            .containsExactly(1L, 2L, 3L, 4L);
        assertThat(selection.picks()).extracting(CompanionPick::reason).containsOnlyNulls();
    }

    @Test
    @DisplayName("PAIR-06 AI가 4개보다 많이 고르면 AI가 고른 순서의 앞 4개만 돌려준다")
    void keepFirstFourAiPicks() {
        List<CompanionCandidate> ranked = rankedCandidates(6);
        List<CompanionAiPick> aiPicks = List.of(
            new CompanionAiPick(6L, "이유 6"),
            new CompanionAiPick(5L, "이유 5"),
            new CompanionAiPick(4L, "이유 4"),
            new CompanionAiPick(3L, "이유 3"),
            new CompanionAiPick(2L, "이유 2"));

        CompanionSelection selection = CompanionSelection.of(aiPicks, ranked);

        assertThat(selection.picks()).extracting(pick -> pick.product().productId())
            .containsExactly(6L, 5L, 4L, 3L);
    }

    @ParameterizedTest
    @CsvSource({"2, 2", "0, 0"})
    @DisplayName("PAIR-06 규칙 후보가 4개보다 적으면 있는 만큼만 돌려준다")
    void returnAllWhenFewerThanFourCandidates(int candidateCount, int expectedPickCount) {
        CompanionSelection selection = CompanionSelection.of(null, rankedCandidates(candidateCount));

        assertThat(selection.source()).isEqualTo(CompanionSource.RULE);
        assertThat(selection.picks()).hasSize(expectedPickCount);
    }

    // 상품 ID 1부터 count까지를 규칙 순서(점수 순)로 둔 후보 목록이다
    private static List<CompanionCandidate> rankedCandidates(int count) {
        List<CompanionCandidate> ranked = new ArrayList<>();
        for (long id = 1; id <= count; id++) {
            RecommendedProduct product = new RecommendedProduct(id, 100L, "상품 " + id, null, 10_000L, "110", 1L,
                null, SALE_STARTED_AT, SALE_STARTED_AT);
            ranked.add(new CompanionCandidate(product, List.of("의류", "하의"), 0.5));
        }
        return ranked;
    }
}
