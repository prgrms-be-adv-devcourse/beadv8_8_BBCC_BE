package com.bbcc.kidly.boundedcontext.recommend.domain;

import com.bbcc.kidly.shared.member.domain.BodyType;
import com.bbcc.kidly.shared.member.domain.Gender;
import com.bbcc.kidly.shared.member.domain.PreferredFit;
import java.time.LocalDate;

public record ChildSizeInput(
        double heightCm,
        double weightKg,
        Gender gender,
        LocalDate birthDate,
        BodyType bodyType,
        PreferredFit preferredFit
) {

    public static ChildSizeInput of(
            double heightCm,
            double weightKg,
            Gender gender,
            LocalDate birthDate,
            BodyType bodyType,
            PreferredFit preferredFit
    ) {
        return new ChildSizeInput(
                heightCm, weightKg, gender,
                birthDate, bodyType, preferredFit
        );
    }
}
