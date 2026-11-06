package com.bbcc.kidly.boundedcontext.recommend.domain.size;

import com.bbcc.kidly.shared.childprofile.domain.Gender;

public record BmiForAgeRow(
        Gender gender,
        int months,
        double percentile5Bmi,
        double percentile85Bmi
) {
}
