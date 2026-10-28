package com.bbcc.kidly.boundedcontext.recommend.domain.size;

import com.bbcc.kidly.shared.childprofile.domain.Gender;

public record GrowthChartRow(
        AgeGroup ageGroup,
        Gender gender,
        double heightCm,
        WeightPercentiles percentiles
) {

    public static GrowthChartRow of(
            AgeGroup ageGroup, Gender gender, double heightCm, WeightPercentiles percentiles) {
        return new GrowthChartRow(ageGroup, gender, heightCm, percentiles);
    }
}
