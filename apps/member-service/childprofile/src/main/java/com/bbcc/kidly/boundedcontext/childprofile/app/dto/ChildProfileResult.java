package com.bbcc.kidly.boundedcontext.childprofile.app.dto;

import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfile;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ChildProfileResult(
    Long id,
    String name,
    LocalDate birthDate,
    Gender gender,
    BigDecimal heightCm,
    BigDecimal weightKg,
    BodyType bodyType,
    PreferredFit preferredFit,
    boolean hasOptionalConsent,
    int growthRecordCount,
    LocalDateTime lastRecordedAt) {

    public static ChildProfileResult from(ChildProfile child) {
        return new ChildProfileResult(
            child.getId(), child.getName(), child.getBirthDate(), child.getGender(), child.getHeightCm(),
            child.getWeightKg(), child.getBodyType(), child.getPreferredFit(), child.hasOptionalConsent(),
            child.growthRecordCount(), child.lastRecordedAt());
    }
}
