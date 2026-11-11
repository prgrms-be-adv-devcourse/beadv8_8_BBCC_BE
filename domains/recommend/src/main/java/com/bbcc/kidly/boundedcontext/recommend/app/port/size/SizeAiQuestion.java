package com.bbcc.kidly.boundedcontext.recommend.app.port.size;

import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceFit;
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
 * @param referenceFit 자녀의 기준 옷과 이 상품의 사이즈별 실측을 비교한 기준 핏이고, 기준 옷이 없으면 null이다
 * @param growthSpeed 자녀의 키 성장 속도다
 */
public record SizeAiQuestion(
    Child child,
    List<SizeLine> sizes,
    RuleResult ruleResult,
    ReferenceFit referenceFit,
    GrowthSpeed growthSpeed) {

    /**
     * 숫자는 소수 둘째 자리까지 반올림한 값이다.
     *
     * @param cmPerMonth 전체 성장 속도(cm/월)
     * @param recentCmPerMonth 최근 3개월 성장 속도(cm/월)이고, 계산할 수 없으면 null이다
     * @param recordCount 계산에 쓴 성장 기록 개수
     * @param recordMonths 첫 기록과 마지막 기록 사이 개월 수
     * @param childSharePercent 전체 속도에서 자녀 기록이 차지하는 비중(0 ~ 100)이다
     * @param tableCmPerMonth 나이 · 성별 표 값(cm/월)
     * @param tableSource 표 출처
     */
    public record GrowthSpeed(
        BigDecimal cmPerMonth,
        BigDecimal recentCmPerMonth,
        int recordCount,
        BigDecimal recordMonths,
        int childSharePercent,
        BigDecimal tableCmPerMonth,
        String tableSource) {
    }

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
