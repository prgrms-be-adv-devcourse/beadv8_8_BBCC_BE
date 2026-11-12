package com.bbcc.kidly.boundedcontext.recommend.domain.growth;

import com.bbcc.kidly.shared.childprofile.domain.Gender;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 나이 · 성별 1년 키 성장량 표다 (한국아동패널 중앙값과 표준편차).
 */
public class GrowthVelocityTable {

    private static final int MONTHS_PER_YEAR = 12;

    private final Map<AgeKey, GrowthVelocityRow> rowsByAge;
    private final long lastAgeYears;
    private final String source;

    private GrowthVelocityTable(Map<AgeKey, GrowthVelocityRow> rowsByAge, long lastAgeYears, String source) {
        this.rowsByAge = rowsByAge;
        this.lastAgeYears = lastAgeYears;
        this.source = source;
    }

    /**
     * @param source 표 출처다. 성장 속도의 근거로 AI에 함께 넘긴다
     */
    public static GrowthVelocityTable of(List<GrowthVelocityRow> rows, String source) {
        Map<AgeKey, GrowthVelocityRow> byAge = new HashMap<>();
        long lastAgeYears = 0;
        for (GrowthVelocityRow row : rows) {
            byAge.put(new AgeKey(row.gender(), row.ageYears()), row);
            lastAgeYears = Math.max(lastAgeYears, row.ageYears());
        }
        return new GrowthVelocityTable(Map.copyOf(byAge), lastAgeYears, source);
    }

    public String source() {
        return source;
    }

    /**
     * 표의 마지막 나이보다 많으면 마지막 구간 값을 쓴다.
     */
    public double yearlyCm(Gender gender, long months) {
        return rowOf(gender, months).medianCmPerYear();
    }

    /**
     * 표의 마지막 나이보다 많으면 마지막 구간 값을 쓴다.
     */
    public double yearlySdCm(Gender gender, long months) {
        return rowOf(gender, months).sdCmPerYear();
    }

    private GrowthVelocityRow rowOf(Gender gender, long months) {
        long ageYears = Math.min(Math.max(0, months) / MONTHS_PER_YEAR, lastAgeYears);
        return rowsByAge.get(new AgeKey(gender, ageYears));
    }

    private record AgeKey(Gender gender, long ageYears) {
    }
}
