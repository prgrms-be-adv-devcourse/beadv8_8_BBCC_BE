package com.bbcc.kidly.boundedcontext.recommend.domain.size;

import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 체형 기준표다. 질병관리청 소아청소년 성장도표의 비만 판정 기준으로 아이의 체형을 판정한다.
 */
public class GrowthChart {

    private final Map<AgeKey, BmiForAgeRow> bmiForAge;
    private final Map<AgeKey, InfantWeightForAgeRow> infantWeightForAge;
    private final Map<LengthKey, InfantWeightForLengthRow> infantWeightForLength;

    private GrowthChart(
            Map<AgeKey, BmiForAgeRow> bmiForAge,
            Map<AgeKey, InfantWeightForAgeRow> infantWeightForAge,
            Map<LengthKey, InfantWeightForLengthRow> infantWeightForLength
    ) {
        this.bmiForAge = bmiForAge;
        this.infantWeightForAge = infantWeightForAge;
        this.infantWeightForLength = infantWeightForLength;
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
        Map<AgeKey, InfantWeightForAgeRow> infantByAge = new HashMap<>();
        for (InfantWeightForAgeRow row : infantWeightForAge) {
            infantByAge.put(new AgeKey(row.gender(), row.months()), row);
        }
        Map<LengthKey, InfantWeightForLengthRow> infantByLength = new HashMap<>();
        for (InfantWeightForLengthRow row : infantWeightForLength) {
            infantByLength.put(LengthKey.of(row.gender(), row.lengthCm()), row);
        }
        return new GrowthChart(Map.copyOf(bmiByAge), Map.copyOf(infantByAge), Map.copyOf(infantByLength));
    }

    public Optional<BodyType> judge(Gender gender, long months, double heightCm, double weightKg) {
        Optional<AgeGroup> ageGroup = AgeGroup.fromMonths(months);
        if (ageGroup.isEmpty()) {
            return Optional.empty();
        }
        if (ageGroup.get() == AgeGroup.MONTHS_0_23) {
            return judgeInfant(gender, months, heightCm, weightKg);
        }
        return judgeByBmi(gender, months, heightCm, weightKg);
    }

    private Optional<BodyType> judgeByBmi(Gender gender, long months, double heightCm, double weightKg) {
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

    /**
     * 통통 기준인 신장별 체중을 먼저 본다. 옷 둘레에 직접 닿는 값이라 마름 기준과 함께 맞으면 통통으로 둔다.
     */
    private Optional<BodyType> judgeInfant(Gender gender, long months, double heightCm, double weightKg) {
        InfantWeightForLengthRow lengthRow = infantWeightForLength.get(LengthKey.of(gender, heightCm));
        InfantWeightForAgeRow ageRow = infantWeightForAge.get(new AgeKey(gender, months));
        if (lengthRow == null || ageRow == null) {
            return Optional.empty();
        }
        if (weightKg >= lengthRow.percentile95Kg()) {
            return Optional.of(BodyType.CHUNKY);
        }
        if (weightKg < ageRow.percentile5Kg()) {
            return Optional.of(BodyType.SLIM);
        }
        return Optional.of(BodyType.NORMAL);
    }

    private record AgeKey(Gender gender, long months) {
    }

    /**
     * 영아 표는 누운키 0.5cm 간격이라 키를 0.5cm 단위로 반올림한 값을 키로 쓴다.
     */
    private record LengthKey(Gender gender, long halfCm) {

        private static LengthKey of(Gender gender, double lengthCm) {
            return new LengthKey(gender, Math.round(lengthCm * 2));
        }
    }
}
