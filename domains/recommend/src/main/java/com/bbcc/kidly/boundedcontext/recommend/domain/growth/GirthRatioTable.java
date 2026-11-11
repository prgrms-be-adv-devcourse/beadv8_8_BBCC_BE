package com.bbcc.kidly.boundedcontext.recommend.domain.growth;

import com.bbcc.kidly.shared.childprofile.domain.Gender;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * 키 1cm가 클 때 가슴 · 허리 · 엉덩이 둘레가 느는 양(cm)의 나이 구간 · 성별 표다 (사이즈코리아).
 */
public class GirthRatioTable {

    private static final int MONTHS_PER_YEAR = 12;

    private final Map<Gender, NavigableMap<Integer, GirthRatioRow>> rowsByGender;

    private GirthRatioTable(Map<Gender, NavigableMap<Integer, GirthRatioRow>> rowsByGender) {
        this.rowsByGender = rowsByGender;
    }

    public static GirthRatioTable of(List<GirthRatioRow> rows) {
        Map<Gender, NavigableMap<Integer, GirthRatioRow>> byGender = new HashMap<>();
        for (GirthRatioRow row : rows) {
            NavigableMap<Integer, GirthRatioRow> ageRows = byGender.get(row.gender());
            if (ageRows == null) {
                ageRows = new TreeMap<>();
                byGender.put(row.gender(), ageRows);
            }
            ageRows.put(row.ageFrom(), row);
        }
        return new GirthRatioTable(byGender);
    }

    /**
     * 만 나이 이하인 구간 중 시작 나이가 가장 큰 구간의 값을 쓴다. 마지막 구간 뒤 나이도 마지막 구간 값이다.
     */
    public double ratio(Gender gender, long months, GirthPart part) {
        int ageYears = (int) (months / MONTHS_PER_YEAR);
        GirthRatioRow row = rowsByGender.get(gender).floorEntry(ageYears).getValue();
        return switch (part) {
            case CHEST -> row.chestCmPerHeightCm();
            case WAIST -> row.waistCmPerHeightCm();
            case HIP -> row.hipCmPerHeightCm();
        };
    }
}
