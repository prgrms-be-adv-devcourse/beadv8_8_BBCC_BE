package com.bbcc.kidly.boundedcontext.recommend.domain.growth;

import com.bbcc.kidly.shared.childprofile.domain.Gender;

public record GirthRatioRow(
        Gender gender,
        int ageFrom,
        double chestCmPerHeightCm,
        double waistCmPerHeightCm,
        double hipCmPerHeightCm
) {
}
