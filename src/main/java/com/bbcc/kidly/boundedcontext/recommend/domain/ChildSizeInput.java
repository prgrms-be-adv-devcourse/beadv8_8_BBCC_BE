package com.bbcc.kidly.boundedcontext.recommend.domain;

import com.bbcc.kidly.shared.member.domain.BodyType;
import com.bbcc.kidly.shared.member.domain.Gender;
import com.bbcc.kidly.shared.member.domain.PreferredFit;
import java.time.YearMonth;

public record ChildSizeInput(
        double heightCm,
        double weightKg,
        Gender gender,
        YearMonth birthMonth,
        BodyType bodyType,
        PreferredFit preferredFit
) {

    public static ChildSizeInput of(
            double heightCm,
            double weightKg,
            Gender gender,
            YearMonth birthMonth,
            BodyType bodyType,
            PreferredFit preferredFit
    ) {
        return new ChildSizeInput(
                heightCm, weightKg, gender,
                birthMonth, bodyType, preferredFit
        );
    }
}
