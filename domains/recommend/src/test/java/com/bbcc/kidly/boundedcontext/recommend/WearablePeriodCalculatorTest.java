package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.growth.WearablePeriod;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.WearablePeriodCalculator;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.WearablePeriodCalculator.WearInfo;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.ProductSizes;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRange;
import com.bbcc.kidly.boundedcontext.recommend.out.growth.GrowthTableConfig;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WearablePeriodCalculatorTest {

    // 오늘 만 6세(78개월)인 여자아이, 키 120cm, 한 달 0.5cm씩 큰다
    private static final long AGE_6_MONTHS = 78;
    private static final double HEIGHT_CM = 120.0;
    private static final double CM_PER_MONTH = 0.5;
    private static final List<String> TOP_PATH = List.of("키즈", "상의");

    private final WearablePeriodCalculator calculator =
        new WearablePeriodCalculator(new GrowthTableConfig().girthRatioTable());

    @Test
    @DisplayName("WEAR-01 길이 기준은 (권장 키 상한 − 지금 키) ÷ 한 달 성장량을 내린 개월 수다")
    void lengthMonthsFromMaxHeight() {
        ProductSizes sizes = ProductSizes.of(false, List.of(
            SizeRange.of("120", 115, 130),
            SizeRange.of("125", 120, 125)));

        List<WearablePeriod> periods = calculator.calculate(
            new WearInfo(sizes, TOP_PATH, Gender.FEMALE, AGE_6_MONTHS, HEIGHT_CM + 0.4, CM_PER_MONTH, null));

        // (130 − 120.4) ÷ 0.5 = 19.2 → 19, (125 − 120.4) ÷ 0.5 = 9.2 → 9
        assertThat(periods).containsExactly(
            new WearablePeriod("120", 19, null, 19),
            new WearablePeriod("125", 9, null, 9));
    }
}
