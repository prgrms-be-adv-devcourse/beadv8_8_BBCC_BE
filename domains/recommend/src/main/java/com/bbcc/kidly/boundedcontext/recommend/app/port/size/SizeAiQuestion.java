package com.bbcc.kidly.boundedcontext.recommend.app.port.size;

import com.bbcc.kidly.boundedcontext.recommend.domain.growth.GrowthVelocity;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.GrowthVelocityCalculator;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.WearablePeriod;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.WearablePeriodCalculator;
import com.bbcc.kidly.boundedcontext.recommend.domain.purchase.PurchaseFit;
import com.bbcc.kidly.boundedcontext.recommend.domain.purchase.PurchaseHistory;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceFit;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeAiEvidence;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * AI에게 사이즈를 고르게 할 때 보내는 질문이다. 자녀 이름 · 생년월일 · 회원 ID는 담지 않는다.
 *
 * @param child 자녀의 몸 정보다
 * @param sizes 상품이 파는 사이즈별 권장 키와 실측이다
 * @param ruleResult 규칙으로 계산한 결과다
 * @param referenceFit 자녀의 기준 옷과 이 상품의 사이즈별 실측을 비교한 기준 핏이고, 기준 옷이 없으면 null이다
 * @param growthSpeed 자녀의 키 성장 속도이고, 늘 있다
 * @param wearablePeriods 사이즈별로 앞으로 몇 개월 입을 수 있는지이고,
 *     사이즈 무관 상품이나 모자 · 양말처럼 계산하지 않는 상품은 빈 목록이다
 * @param purchases 이 자녀가 12개월 안에 같은 기준 옷 칸에서 구매확정한 최근 구매(최대 3개)를 지금 상품과 견준 결과이고,
 *     같은 칸의 구매가 없거나 사이즈 무관 상품이나 모자 · 양말이면 빈 목록이다
 */
public record SizeAiQuestion(
    Child child,
    List<SizeLine> sizes,
    RuleResult ruleResult,
    ReferenceFit referenceFit,
    GrowthSpeed growthSpeed,
    List<WearablePeriod> wearablePeriods,
    List<PurchaseFit> purchases) {

    public SizeAiQuestion {
        Objects.requireNonNull(growthSpeed, "growthSpeed는 필수입니다");
        Objects.requireNonNull(wearablePeriods, "wearablePeriods는 필수입니다");
        Objects.requireNonNull(purchases, "purchases는 필수입니다");
    }

    /**
     * 이 질문에 든 숫자를 모은다. AI 답의 근거 문장에 쓴 숫자가 질문에 있는 숫자인지 검증할 때 쓴다.
     * 지시 글에 나오는 기간(최근 3개월 · 12개월 · 상한 24개월)도 AI가 문장에 쓸 수 있어 함께 담는다.
     */
    public Set<BigDecimal> givenNumbers() {
        Set<BigDecimal> numbers = new HashSet<>();
        numbers.add(BigDecimal.valueOf(child.ageMonths()));
        numbers.add(BigDecimal.valueOf(child.heightCm()));
        numbers.add(BigDecimal.valueOf(child.weightKg()));
        for (SizeLine line : sizes) {
            addIfNumber(numbers, line.size());
            addIfNumber(numbers, line.minHeightCm());
            addIfNumber(numbers, line.maxHeightCm());
            numbers.addAll(line.measurements().values());
        }
        addIfNumber(numbers, ruleResult.recommendedSize());
        for (String candidateSize : ruleResult.candidateSizes()) {
            addIfNumber(numbers, candidateSize);
        }
        addReferenceFitNumbers(numbers);
        addGrowthNumbers(numbers);
        addPurchaseNumbers(numbers);
        return numbers;
    }

    // 지난 구매의 산 옷 실측 · 실측 차이 · 개월 수 · 예상 성장량과, 지시 글에 나오는 최근 개수 · 기간을 담는다
    private void addPurchaseNumbers(Set<BigDecimal> numbers) {
        for (PurchaseFit purchase : purchases) {
            addIfNumber(numbers, purchase.size());
            addIfNumber(numbers, purchase.monthsAgo());
            numbers.add(purchase.expectedGrowthCm());
            addItemNumbers(numbers, purchase.items());
        }
        addIfNumber(numbers, PurchaseHistory.MAX_PURCHASES);
        addIfNumber(numbers, PurchaseHistory.WINDOW_MONTHS);
    }

    private void addReferenceFitNumbers(Set<BigDecimal> numbers) {
        if (referenceFit == null) {
            return;
        }
        addItemNumbers(numbers, referenceFit.items());
    }

    // 실측 항목마다 견준 옷의 실측과 사이즈별 상품 실측 · 차이를 담는다
    private static void addItemNumbers(Set<BigDecimal> numbers, List<ReferenceFit.Item> items) {
        for (ReferenceFit.Item item : items) {
            numbers.add(item.garmentCm());
            for (ReferenceFit.SizeDiff diff : item.sizes()) {
                numbers.add(diff.productCm());
                numbers.add(diff.diffCm());
            }
        }
    }

    private void addGrowthNumbers(Set<BigDecimal> numbers) {
        numbers.add(growthSpeed.cmPerMonth());
        if (growthSpeed.recentCmPerMonth() != null) {
            numbers.add(growthSpeed.recentCmPerMonth());
        }
        addIfNumber(numbers, growthSpeed.recordCount());
        numbers.add(growthSpeed.recordMonths());
        addIfNumber(numbers, growthSpeed.childSharePercent());
        numbers.add(growthSpeed.tableCmPerMonth());
        for (WearablePeriod period : wearablePeriods) {
            addIfNumber(numbers, period.lengthMonths());
            addIfNumber(numbers, period.widthMonths());
            addIfNumber(numbers, period.months());
        }
        addIfNumber(numbers, GrowthVelocityCalculator.RECENT_MONTHS);
        addIfNumber(numbers, GrowthVelocityCalculator.WINDOW_MONTHS);
        addIfNumber(numbers, WearablePeriodCalculator.MAX_MONTHS);
    }

    private static void addIfNumber(Set<BigDecimal> numbers, Integer value) {
        if (value != null) {
            numbers.add(BigDecimal.valueOf(value));
        }
    }

    // 「110」이나 「130-150mm」처럼 사이즈 이름에 든 숫자를 담고, 「FREE」나 「M」은 숫자가 없어 건너뛴다. null도 건너뛴다.
    private static void addIfNumber(Set<BigDecimal> numbers, String sizeName) {
        if (sizeName != null) {
            numbers.addAll(SizeAiEvidence.numbersIn(sizeName));
        }
    }

    /**
     * 지난 AI 답을 찾는 키에 넣을 글이다. 답에 영향을 주는 기준 핏 · 성장 속도 · 입을 수 있는 기간 · 지난 구매를
     * 늘 같은 순서의 글로 이어 붙이고, 이 질문에 넣은 반올림 값으로 만든다.
     */
    public String keyText() {
        StringBuilder text = new StringBuilder();
        if (referenceFit != null) {
            text.append(referenceFit.keyText());
        }
        text.append("|growthSpeed=").append(plain(growthSpeed.cmPerMonth())).append(',')
            .append(plain(growthSpeed.recentCmPerMonth())).append(',')
            .append(growthSpeed.recordCount()).append(',')
            .append(plain(growthSpeed.recordMonths())).append(',')
            .append(growthSpeed.childSharePercent()).append(',')
            .append(plain(growthSpeed.tableCmPerMonth())).append(',')
            .append(growthSpeed.tableSource());
        text.append("|wearablePeriods=");
        for (WearablePeriod period : wearablePeriods) {
            text.append(period.size()).append(':')
                .append(Objects.toString(period.lengthMonths(), "")).append(':')
                .append(Objects.toString(period.widthMonths(), "")).append(':')
                .append(Objects.toString(period.months(), "")).append(';');
        }
        text.append("|purchases=");
        for (PurchaseFit purchase : purchases) {
            appendPurchaseKey(text, purchase);
        }
        return text.toString();
    }

    // 구매 하나를 「사이즈:개월:같은 브랜드:예상 성장량」 뒤에 기준 핏과 같은 형식의 항목 글을 이어 붙이고 「#」으로 끝낸다
    private static void appendPurchaseKey(StringBuilder text, PurchaseFit purchase) {
        text.append(purchase.size()).append(':').append(purchase.monthsAgo()).append(':')
            .append(purchase.sameBrand()).append(':').append(plain(purchase.expectedGrowthCm()));
        for (ReferenceFit.Item item : purchase.items()) {
            text.append(item.keyText());
        }
        text.append('#');
    }

    private static String plain(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    /**
     * 숫자는 소수 둘째 자리까지 반올림한 값이다.
     *
     * @param cmPerMonth 전체 성장 속도(cm/월)다
     * @param recentCmPerMonth 최근 3개월 성장 속도(cm/월)이고, 계산할 수 없으면 null이다
     * @param recordCount 계산에 쓴 성장 기록 개수다
     * @param recordMonths 첫 기록과 마지막 기록 사이 개월 수다
     * @param childSharePercent 전체 속도에서 자녀 기록이 차지하는 비중(0 ~ 100)이다
     * @param tableCmPerMonth 나이 · 성별 표 값(cm/월)이다
     * @param tableSource 표 출처다
     */
    public record GrowthSpeed(
        BigDecimal cmPerMonth,
        BigDecimal recentCmPerMonth,
        int recordCount,
        BigDecimal recordMonths,
        int childSharePercent,
        BigDecimal tableCmPerMonth,
        String tableSource) {

        private static final int CM_SCALE = 2;
        private static final int PERCENT = 100;

        /**
         * 계산한 성장 속도를 AI에게 줄 값으로 바꾼다. cm 값은 소수 둘째 자리에서 반올림하고 자녀 기록 비중은 퍼센트 정수로 바꾼다.
         */
        public static GrowthSpeed from(GrowthVelocity velocity) {
            return new GrowthSpeed(
                rounded(velocity.cmPerMonth()), roundedOrNull(velocity.recentCmPerMonth()), velocity.recordCount(),
                rounded(velocity.recordMonths()), (int) Math.round(velocity.childShare() * PERCENT),
                rounded(velocity.tableCmPerMonth()), velocity.tableSource());
        }

        private static BigDecimal rounded(double value) {
            return BigDecimal.valueOf(value).setScale(CM_SCALE, RoundingMode.HALF_UP);
        }

        private static BigDecimal roundedOrNull(Double value) {
            if (value == null) {
                return null;
            }
            return rounded(value);
        }
    }

    /**
     * @param bodyType 보호자가 입력한 체형이다
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
