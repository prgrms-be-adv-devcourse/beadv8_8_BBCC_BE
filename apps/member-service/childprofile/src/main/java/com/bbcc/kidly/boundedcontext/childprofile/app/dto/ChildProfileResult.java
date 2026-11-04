package com.bbcc.kidly.boundedcontext.childprofile.app.dto;

import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfile;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 자녀 조회 결과. 컨트롤러는 엔티티 대신 이것으로 응답을 만든다.
 *
 * @param bodyType 입력하지 않았으면 null
 * @param preferredFit 입력하지 않았으면 null
 * @param growthRecordCount 성장 이력 건수
 * @param lastRecordedAt 마지막 성장 이력 시각
 */
public record ChildProfileResult(
    Long id,
    String name,
    LocalDate birthDate,
    Gender gender,
    BigDecimal heightCm,
    BigDecimal weightKg,
    BodyType bodyType,
    PreferredFit preferredFit,
    int growthRecordCount,
    LocalDateTime lastRecordedAt) {

    public static ChildProfileResult from(ChildProfile child) {
        return new ChildProfileResult(
            child.getId(), child.getName(), child.getBirthDate(), child.getGender(), child.getHeightCm(),
            child.getWeightKg(), child.getBodyType(), child.getPreferredFit(), child.growthRecordCount(),
            child.lastRecordedAt());
    }
}
