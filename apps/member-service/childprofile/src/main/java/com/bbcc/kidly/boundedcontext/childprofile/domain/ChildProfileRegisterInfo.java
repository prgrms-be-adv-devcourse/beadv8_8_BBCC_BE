package com.bbcc.kidly.boundedcontext.childprofile.domain;

import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ChildProfileRegisterInfo(
    Long memberId,
    String name,
    LocalDate birthDate,
    Gender gender,
    BigDecimal heightCm,
    BigDecimal weightKg,
    BodyType bodyType,
    PreferredFit preferredFit,
    boolean optionalConsent) {
}
