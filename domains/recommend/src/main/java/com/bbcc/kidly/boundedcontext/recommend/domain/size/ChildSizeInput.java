package com.bbcc.kidly.boundedcontext.recommend.domain.size;

import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
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
