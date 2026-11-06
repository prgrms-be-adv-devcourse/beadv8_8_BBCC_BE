package com.bbcc.kidly.boundedcontext.recommend.domain.size;

import com.bbcc.kidly.shared.childprofile.domain.Gender;

public record InfantWeightForAgeRow(
        Gender gender,
        int months,
        double percentile5Kg
) {
}
