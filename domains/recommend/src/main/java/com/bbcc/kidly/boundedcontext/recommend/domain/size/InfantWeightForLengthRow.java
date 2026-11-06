package com.bbcc.kidly.boundedcontext.recommend.domain.size;

import com.bbcc.kidly.shared.childprofile.domain.Gender;

public record InfantWeightForLengthRow(
        Gender gender,
        double lengthCm,
        double percentile95Kg
) {
}
