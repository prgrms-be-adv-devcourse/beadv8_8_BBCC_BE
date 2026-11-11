package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentPart;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceFit;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.AiSizeAnswer;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.Fit;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.ItemJudgement;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeAiEvidence;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRecommendation;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRecommendationSource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class SizeRecommendationJudgementTest {

    private static final SizeRecommendation RULE_120 =
        SizeRecommendation.recommended("120", Fit.PROPER, List.of(), List.of("110", "120"));

    // 상의 기준 옷 가슴단면 34cm와 상품 110 · 120의 가슴단면 33 · 36cm를 비교한 기준 핏이다
    private static final ReferenceFit REFERENCE_FIT = new ReferenceFit(GarmentPart.TOP, GarmentPart.TOP, List.of(
        new ReferenceFit.Item("CHEST_WIDTH", cm("34.0"), List.of(
            new ReferenceFit.SizeDiff("110", cm("33.0"), cm("-1.0")),
            new ReferenceFit.SizeDiff("120", cm("36.0"), cm("2.0"))))));

    private static final SizeAiEvidence GIVEN = new SizeAiEvidence(REFERENCE_FIT,
        Set.of(cm("110"), cm("120"), cm("34.0"), cm("33.0"), cm("36.0"), cm("-1.0"), cm("2.0")));

    @Test
    @DisplayName("FIT-09 AI 답을 채택하면 추천 사이즈의 항목별 판단(항목 · 차이 · 판단 · 여유 범위)을 결과에 담는다")
    void keepItemJudgementsOfAdoptedAnswer() {
        AiSizeAnswer answer = new AiSizeAnswer("120", "PROPER",
            List.of(new AiSizeAnswer.Reason("MEASUREMENT", "가슴단면이 지금 옷보다 2cm 넉넉해요.", null)),
            List.of(new AiSizeAnswer.Judgement("CHEST_WIDTH", cm("2.0"), "ROOMY", cm("0.0"), cm("3.0"))));

        SizeRecommendation result = RULE_120.decideWith(RecommendFixture.productA(), answer, GIVEN);

        assertThat(result.getSource()).isEqualTo(SizeRecommendationSource.AI);
        assertThat(result.getItemJudgements())
            .usingRecursiveFieldByFieldElementComparator(cmComparison())
            .containsExactly(new ItemJudgement("CHEST_WIDTH", cm("2.0"), Fit.ROOMY, cm("0.0"), cm("3.0")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("judgementsNotMatchingGiven")
    @DisplayName("FIT-10 항목별 판단이 넘긴 기준 핏(추천 사이즈의 차이)과 맞지 않으면 AI 답을 버리고 규칙 결과로 답한다")
    void fallBackToRuleWhenJudgementDoesNotMatch(AiSizeAnswer.Judgement judgement, SizeAiEvidence evidence) {
        List<AiSizeAnswer.Judgement> judgements = new ArrayList<>();
        judgements.add(judgement);
        AiSizeAnswer answer = new AiSizeAnswer("120", "ROOMY",
            List.of(new AiSizeAnswer.Reason("HEIGHT", "120을 골랐어요.", null)), judgements);

        SizeRecommendation result = RULE_120.decideWith(RecommendFixture.productA(), answer, evidence);

        assertThat(result.getSource()).isEqualTo(SizeRecommendationSource.RULE);
        assertThat(result.getRecommendedSize()).isEqualTo("120");
        assertThat(result.getFit()).isEqualTo(Fit.PROPER);
        assertThat(result.getItemJudgements()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"가슴단면이 지금 옷보다 3cm 넉넉해요.", "키 130cm에 맞는 사이즈예요.", "5년은 더 입을 수 있어요."})
    @DisplayName("FIT-10 근거 문장에 AI에게 주지 않은 숫자가 있으면 AI 답을 버리고 규칙 결과로 답한다")
    void fallBackToRuleWhenReasonHasUnknownNumber(String detail) {
        AiSizeAnswer answer = new AiSizeAnswer("120", "ROOMY",
            List.of(new AiSizeAnswer.Reason("MEASUREMENT", detail, null)), List.of());

        SizeRecommendation result = RULE_120.decideWith(RecommendFixture.productA(), answer, GIVEN);

        assertThat(result.getSource()).isEqualTo(SizeRecommendationSource.RULE);
        assertThat(result.getRecommendedSize()).isEqualTo("120");
    }

    // 준 숫자는 110 · 120 · 34.0 · 33.0 · 36.0 · -1.0 · 2.0이고, 판단의 여유 범위는 0.0 ~ 3.0이다
    @ParameterizedTest
    @ValueSource(strings = {
        "가슴단면이 지금 옷보다 2cm 넉넉해요.",
        "110은 지금 옷보다 가슴이 1cm 작아요.",
        "여유 0 ~ 3cm 안이라 맞아요.",
        "가슴단면 36.0cm로 지금 옷 34cm보다 커요.",
        "숫자 없이 고른 이유예요."})
    @DisplayName("FIT-10 근거 문장의 숫자가 준 숫자(부호를 뺀 값 포함)나 판단의 여유 범위이면 AI 답을 쓴다")
    void adoptAnswerWhenReasonNumbersAreGiven(String detail) {
        AiSizeAnswer answer = new AiSizeAnswer("120", "ROOMY",
            List.of(new AiSizeAnswer.Reason("MEASUREMENT", detail, null)),
            List.of(new AiSizeAnswer.Judgement("CHEST_WIDTH", cm("2.0"), "ROOMY", cm("0.0"), cm("3.0"))));

        SizeRecommendation result = RULE_120.decideWith(RecommendFixture.productA(), answer, GIVEN);

        assertThat(result.getSource()).isEqualTo(SizeRecommendationSource.AI);
    }

    private static Stream<Arguments> judgementsNotMatchingGiven() {
        SizeAiEvidence withoutReferenceFit = new SizeAiEvidence(null, GIVEN.givenNumbers());
        return Stream.of(
            Arguments.of(Named.of("차이를 2.0 대신 2.5로 적음",
                judgement("CHEST_WIDTH", "2.5", "ROOMY", "0.0", "3.0")), GIVEN),
            Arguments.of(Named.of("추천 사이즈 120이 아닌 110의 차이를 적음",
                judgement("CHEST_WIDTH", "-1.0", "ROOMY", "0.0", "3.0")), GIVEN),
            Arguments.of(Named.of("비교하지 않은 항목을 적음",
                judgement("SHOULDER_WIDTH", "2.0", "ROOMY", "0.0", "3.0")), GIVEN),
            Arguments.of(Named.of("판단이 모르는 값",
                judgement("CHEST_WIDTH", "2.0", "LOOSE", "0.0", "3.0")), GIVEN),
            Arguments.of(Named.of("여유 범위를 비움",
                judgement("CHEST_WIDTH", "2.0", "ROOMY", null, "3.0")), GIVEN),
            Arguments.of(Named.of("여유 범위 최소가 최대보다 큼",
                judgement("CHEST_WIDTH", "2.0", "ROOMY", "3.0", "0.0")), GIVEN),
            Arguments.of(Named.of("판단 칸이 null", null), GIVEN),
            Arguments.of(Named.of("기준 핏을 넘기지 않았는데 판단을 적음",
                judgement("CHEST_WIDTH", "2.0", "ROOMY", "0.0", "3.0")), withoutReferenceFit));
    }

    private static AiSizeAnswer.Judgement judgement(String code, String diffCm, String fit,
        String allowanceMinCm, String allowanceMaxCm) {
        return new AiSizeAnswer.Judgement(code, cmOrNull(diffCm), fit, cmOrNull(allowanceMinCm),
            cmOrNull(allowanceMaxCm));
    }

    private static BigDecimal cmOrNull(String value) {
        return value == null ? null : cm(value);
    }

    // 2와 2.0처럼 소수 자리만 다른 값은 같은 cm로 본다
    private static RecursiveComparisonConfiguration cmComparison() {
        return RecursiveComparisonConfiguration.builder()
            .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
            .build();
    }

    private static BigDecimal cm(String value) {
        return new BigDecimal(value);
    }
}
