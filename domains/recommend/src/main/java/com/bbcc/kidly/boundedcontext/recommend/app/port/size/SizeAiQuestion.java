package com.bbcc.kidly.boundedcontext.recommend.app.port.size;

import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * AI에게 사이즈를 고르게 할 때 보내는 질문이다. 자녀 이름 · 생년월일 · 회원 ID는 담지 않는다.
 *
 * @param child 자녀의 몸 정보
 * @param sizes 상품이 파는 사이즈별 권장 키와 실측
 * @param ruleResult 규칙으로 계산한 결과
 * @param referenceFit 기준 핏이다. 아직 읽지 않아 늘 null이다
 * @param growthSpeed 성장 속도다. 아직 계산하지 않아 늘 null이다
 */
public record SizeAiQuestion(
    Child child,
    List<SizeLine> sizes,
    RuleResult ruleResult,
    String referenceFit,
    String growthSpeed) {

    /**
     * @param bodyType 보호자가 입력한 체형
     * @param judgedBodyType 키와 몸무게로 판정해 사이즈를 고를 때 쓰는 체형이고, 체형을 쓰지 않으면 null이다
     */
    public record Child(
        int ageMonths,
        double heightCm,
        double weightKg,
        BodyType bodyType,
        PreferredFit preferredFit,
        BodyType judgedBodyType) {
    }

    /**
     * @param measurements 키는 실측 항목 코드, 값은 cm다
     */
    public record SizeLine(
        String size,
        Integer minHeightCm,
        Integer maxHeightCm,
        Map<String, BigDecimal> measurements) {
    }

    /**
     * @param recommendedSize 규칙이 고른 사이즈이고, 없으면 null이다
     * @param fit 규칙이 본 적합도의 이름이고, 없으면 null이다
     * @param candidateSizes 규칙의 후보 사이즈이고, 없으면 빈 목록이다
     * @param notices 규칙이 붙인 안내 코드의 이름이다
     */
    public record RuleResult(
        String recommendedSize,
        String fit,
        List<String> candidateSizes,
        List<String> notices) {
    }
}
