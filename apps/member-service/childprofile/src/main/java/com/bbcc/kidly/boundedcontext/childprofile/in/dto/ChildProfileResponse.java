package com.bbcc.kidly.boundedcontext.childprofile.in.dto;

import com.bbcc.kidly.boundedcontext.childprofile.app.dto.ChildProfileResult;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @param bodyType 입력하지 않았으면 null
 * @param preferredFit 입력하지 않았으면 null
 * @param growthRecordCount 성장 이력 건수
 * @param lastRecordedAt 마지막 성장 이력 시각
 */
public record ChildProfileResponse(
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

    public static ChildProfileResponse from(ChildProfileResult result) {
        return new ChildProfileResponse(
            result.id(), result.name(), result.birthDate(), result.gender(), result.heightCm(), result.weightKg(),
            result.bodyType(), result.preferredFit(), result.hasOptionalConsent(),
            result.growthRecordCount(), result.lastRecordedAt());
    }
}
