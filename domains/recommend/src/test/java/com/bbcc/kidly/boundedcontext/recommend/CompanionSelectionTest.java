package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionAiPick;
import com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionCandidate;
import com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionKind;
import com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionPick;
import com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionSelection;
import com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionSource;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.RecommendedProduct;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CompanionSelectionTest {

    private static final LocalDateTime SALE_STARTED_AT = LocalDateTime.of(2026, 11, 1, 10, 0);

    @Test
    @DisplayName("AI가 후보에서 고른 상품을 AI가 고른 순서대로 이유와 함께 돌려주고, 출처는 AI다")
    void useAiPicksInOrderWithReasons() {
        List<CompanionCandidate> ranked = rankedCandidates(6);
        List<CompanionAiPick> aiPicks = List.of(
            new CompanionAiPick(5L, "같은 색 계열이라 함께 입기 좋다"),
            new CompanionAiPick(2L, "가을에 겹쳐 입기 좋다"),
            new CompanionAiPick(6L, "활동하기 편한 핏이다"),
            new CompanionAiPick(1L, "기본 아이템이라 어디에나 어울린다"));

        CompanionSelection selection = CompanionSelection.of(aiPicks, ranked);

        assertThat(selection.source()).isEqualTo(CompanionSource.AI);
        assertThat(selection.picks()).extracting(pick -> pick.product().productId())
            .containsExactly(5L, 2L, 6L, 1L);
        assertThat(selection.picks()).extracting(CompanionPick::reason)
            .containsExactly("같은 색 계열이라 함께 입기 좋다", "가을에 겹쳐 입기 좋다", "활동하기 편한 핏이다",
                "기본 아이템이라 어디에나 어울린다");
    }

    // 상품 ID 1부터 count까지를 규칙 순서(점수 순)로 둔 후보 목록이다
    private static List<CompanionCandidate> rankedCandidates(int count) {
        List<CompanionCandidate> ranked = new ArrayList<>();
        for (long id = 1; id <= count; id++) {
            RecommendedProduct product = new RecommendedProduct(id, 100L, "상품 " + id, null, 10_000L, "110", 1L,
                null, SALE_STARTED_AT, SALE_STARTED_AT);
            ranked.add(new CompanionCandidate(product, CompanionKind.BOTTOM, 0.5));
        }
        return ranked;
    }
}
