package com.bbcc.kidly.boundedcontext.recommend.domain.growth;

import com.bbcc.kidly.shared.childprofile.domain.Gender;

public record GrowthVelocityRow(
        Gender gender,
        int ageYears,
        double medianCmPerYear,
        double sdCmPerYear
) {
}
