package com.bbcc.kidly.boundedcontext.recommend.domain.size;

import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 체형표다. 연령 구간, 성별, 키로 15백분위와 85백분위 몸무게를 찾는다.
 */
public class GrowthChart {

    private final Map<ChartKey, WeightPercentiles> rows;
    private final Map<AgeKey, BmiForAgeRow> bmiForAge;

    private GrowthChart(Map<ChartKey, WeightPercentiles> rows, Map<AgeKey, BmiForAgeRow> bmiForAge) {
        this.rows = rows;
        this.bmiForAge = bmiForAge;
    }

    public static GrowthChart of(List<GrowthChartRow> rows) {
        Map<ChartKey, WeightPercentiles> byKey = new HashMap<>();
        for (GrowthChartRow row : rows) {
            byKey.put(ChartKey.of(row.ageGroup(), row.gender(), row.heightCm()), row.percentiles());
        }
        return new GrowthChart(Map.copyOf(byKey), Map.of());
    }

    public static GrowthChart of(
            List<BmiForAgeRow> bmiForAge,
            List<InfantWeightForAgeRow> infantWeightForAge,
            List<InfantWeightForLengthRow> infantWeightForLength
    ) {
        Map<AgeKey, BmiForAgeRow> bmiByAge = new HashMap<>();
        for (BmiForAgeRow row : bmiForAge) {
            bmiByAge.put(new AgeKey(row.gender(), row.months()), row);
        }
        return new GrowthChart(Map.of(), Map.copyOf(bmiByAge));
    }

    public Optional<WeightPercentiles> find(AgeGroup ageGroup, Gender gender, double heightCm) {
        return Optional.ofNullable(rows.get(ChartKey.of(ageGroup, gender, heightCm)));
    }

    public Optional<BodyType> judge(Gender gender, long months, double heightCm, double weightKg) {
        BmiForAgeRow row = bmiForAge.get(new AgeKey(gender, months));
        if (row == null) {
            return Optional.empty();
        }
        double heightM = heightCm / 100;
        double bmi = weightKg / (heightM * heightM);
        if (bmi < row.percentile5Bmi()) {
            return Optional.of(BodyType.SLIM);
        }
        if (bmi >= row.percentile85Bmi()) {
            return Optional.of(BodyType.CHUNKY);
        }
        return Optional.of(BodyType.NORMAL);
    }

    private record AgeKey(Gender gender, long months) {
    }

    private record ChartKey(AgeGroup ageGroup, Gender gender, long heightMm) {

        private static ChartKey of(AgeGroup ageGroup, Gender gender, double heightCm) {
            return new ChartKey(ageGroup, gender, Math.round(heightCm * 10));
        }
    }
}
