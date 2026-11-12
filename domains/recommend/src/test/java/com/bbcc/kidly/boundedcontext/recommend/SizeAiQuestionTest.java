package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.port.size.SizeAiQuestion;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.GrowthVelocity;
import com.bbcc.kidly.boundedcontext.recommend.domain.purchase.PurchaseFit;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceFit;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SizeAiQuestionTest {

    @Test
    @DisplayName("성장 속도는 AI에게 줄 때 cm 값을 소수 둘째 자리에서 반올림하고 아이 기록 비중을 퍼센트 정수로 바꾼다")
    void roundGrowthSpeedForQuestion() {
        GrowthVelocity velocity = new GrowthVelocity(0.125, 0.9049, 0.7, 3, 5.954, 0.905, 0.5, "한국아동패널");

        SizeAiQuestion.GrowthSpeed growthSpeed = SizeAiQuestion.GrowthSpeed.from(velocity);

        assertThat(growthSpeed).isEqualTo(new SizeAiQuestion.GrowthSpeed(new BigDecimal("0.13"),
            new BigDecimal("0.90"), 3, new BigDecimal("5.95"), 91, new BigDecimal("0.50"), "한국아동패널"));
    }

    @Test
    @DisplayName("최근 속도를 계산할 수 없으면 AI에게 주는 최근 속도도 null이다")
    void keepMissingRecentSpeedNull() {
        GrowthVelocity velocity = new GrowthVelocity(0.5, null, null, 0, 0.0, 0.0, 0.5, "한국아동패널");

        assertThat(SizeAiQuestion.GrowthSpeed.from(velocity).recentCmPerMonth()).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("purchasesDifferentFromBase")
    @DisplayName("PUR-05 지난 AI 답을 찾는 키 글은 구매의 사이즈 · 개월 · 같은 브랜드 · 예상 성장량 · 실측 차이 중 하나만 달라도 달라진다")
    void keyTextChangesWithEachPurchaseValue(String changed, PurchaseFit purchase) {
        assertThat(questionWith(purchase).keyText()).isNotEqualTo(questionWith(basePurchase()).keyText());
    }

    private static Stream<Arguments> purchasesDifferentFromBase() {
        PurchaseFit base = basePurchase();
        return Stream.of(
            Arguments.of("사이즈", new PurchaseFit("120", base.monthsAgo(), base.sameBrand(), base.expectedGrowthCm(),
                base.items(), null)),
            Arguments.of("개월", new PurchaseFit(base.size(), 6, base.sameBrand(), base.expectedGrowthCm(),
                base.items(), null)),
            Arguments.of("같은 브랜드", new PurchaseFit(base.size(), base.monthsAgo(), false, base.expectedGrowthCm(),
                base.items(), null)),
            Arguments.of("예상 성장량", new PurchaseFit(base.size(), base.monthsAgo(), base.sameBrand(),
                new BigDecimal("3.0"), base.items(), null)),
            Arguments.of("실측 차이", new PurchaseFit(base.size(), base.monthsAgo(), base.sameBrand(),
                base.expectedGrowthCm(), List.of(new ReferenceFit.Item("CHEST_WIDTH", new BigDecimal("32"),
                    List.of(new ReferenceFit.SizeDiff("120", new BigDecimal("36"), new BigDecimal("4"))))), null)));
    }

    // 다섯 달 전 같은 브랜드에서 산 110(가슴단면 33cm)을 상품 120(36cm)과 견준 구매다
    private static PurchaseFit basePurchase() {
        return new PurchaseFit("110", 5, true, new BigDecimal("2.5"), List.of(new ReferenceFit.Item("CHEST_WIDTH",
            new BigDecimal("33"), List.of(new ReferenceFit.SizeDiff("120", new BigDecimal("36"),
                new BigDecimal("3"))))), null);
    }

    private static SizeAiQuestion questionWith(PurchaseFit purchase) {
        return new SizeAiQuestion(
            new SizeAiQuestion.Child(94, 115.0, 20.0, BodyType.NORMAL, PreferredFit.LOOSE, BodyType.NORMAL),
            List.of(new SizeAiQuestion.SizeLine("120", 115, 125, Map.of("CHEST_WIDTH", new BigDecimal("36")))),
            new SizeAiQuestion.RuleResult("120", "PROPER", List.of("120"), List.of()), null,
            new SizeAiQuestion.GrowthSpeed(new BigDecimal("0.50"), null, 0, new BigDecimal("0"), 0,
                new BigDecimal("0.50"), "한국아동패널"),
            List.of(), List.of(purchase));
    }
}
