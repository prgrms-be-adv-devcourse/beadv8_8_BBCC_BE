package com.bbcc.kidly.boundedcontext.recommend.domain.size;

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

    private GrowthChart(Map<ChartKey, WeightPercentiles> rows) {
        this.rows = rows;
    }

    public static GrowthChart of(List<GrowthChartRow> rows) {
        Map<ChartKey, WeightPercentiles> byKey = new HashMap<>();
        for (GrowthChartRow row : rows) {
            byKey.put(ChartKey.of(row.ageGroup(), row.gender(), row.heightCm()), row.percentiles());
        }
        return new GrowthChart(Map.copyOf(byKey));
    }

    public Optional<WeightPercentiles> find(AgeGroup ageGroup, Gender gender, double heightCm) {
        return Optional.ofNullable(rows.get(ChartKey.of(ageGroup, gender, heightCm)));
    }

    private record ChartKey(AgeGroup ageGroup, Gender gender, long heightMm) {

        private static ChartKey of(AgeGroup ageGroup, Gender gender, double heightCm) {
            return new ChartKey(ageGroup, gender, Math.round(heightCm * 10));
        }
    }
}
