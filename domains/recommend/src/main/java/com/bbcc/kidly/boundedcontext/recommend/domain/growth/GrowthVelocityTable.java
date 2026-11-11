package com.bbcc.kidly.boundedcontext.recommend.domain.growth;

import com.bbcc.kidly.shared.childprofile.domain.Gender;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 나이 · 성별 1년 키 성장량 표다 (한국아동패널 중앙값).
 */
public class GrowthVelocityTable {

    private static final int MONTHS_PER_YEAR = 12;

    private final Map<AgeKey, Double> medianCmPerYear;

    private GrowthVelocityTable(Map<AgeKey, Double> medianCmPerYear) {
        this.medianCmPerYear = medianCmPerYear;
    }

    public static GrowthVelocityTable of(List<GrowthVelocityRow> rows) {
        Map<AgeKey, Double> byAge = new HashMap<>();
        for (GrowthVelocityRow row : rows) {
            byAge.put(new AgeKey(row.gender(), row.ageYears()), row.medianCmPerYear());
        }
        return new GrowthVelocityTable(Map.copyOf(byAge));
    }

    public double yearlyCm(Gender gender, long months) {
        return medianCmPerYear.get(new AgeKey(gender, months / MONTHS_PER_YEAR));
    }

    private record AgeKey(Gender gender, long ageYears) {
    }
}
