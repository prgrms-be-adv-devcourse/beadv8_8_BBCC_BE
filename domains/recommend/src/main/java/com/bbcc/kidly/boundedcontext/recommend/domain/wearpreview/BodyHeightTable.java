package com.bbcc.kidly.boundedcontext.recommend.domain.wearpreview;

import com.bbcc.kidly.shared.childprofile.domain.Gender;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 나이 · 성별 신체 높이 비율 표다(사이즈코리아 중앙값).
 */
public class BodyHeightTable {

    private static final int MONTHS_PER_YEAR = 12;

    private final Map<AgeKey, BodyHeightRow> rowsByAge;
    private final long lastAgeYears;

    private BodyHeightTable(Map<AgeKey, BodyHeightRow> rowsByAge, long lastAgeYears) {
        this.rowsByAge = rowsByAge;
        this.lastAgeYears = lastAgeYears;
    }

    public static BodyHeightTable of(List<BodyHeightRow> rows) {
        Map<AgeKey, BodyHeightRow> byAge = new HashMap<>();
        long lastAgeYears = 0;
        for (BodyHeightRow row : rows) {
            byAge.put(new AgeKey(row.gender(), row.ageYears()), row);
            lastAgeYears = Math.max(lastAgeYears, row.ageYears());
        }
        return new BodyHeightTable(Map.copyOf(byAge), lastAgeYears);
    }

    /**
     * 표의 마지막 나이보다 많으면 마지막 나이의 값을 쓴다.
     */
    public BodyHeightRow ratios(Gender gender, long months) {
        long ageYears = Math.min(months / MONTHS_PER_YEAR, lastAgeYears);
        return rowsByAge.get(new AgeKey(gender, ageYears));
    }

    private record AgeKey(Gender gender, long ageYears) {
    }
}
