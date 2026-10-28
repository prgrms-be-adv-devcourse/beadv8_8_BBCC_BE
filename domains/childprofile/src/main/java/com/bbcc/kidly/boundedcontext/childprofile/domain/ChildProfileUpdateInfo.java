package com.bbcc.kidly.boundedcontext.childprofile.domain;

import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * {@link ChildProfile#update} 입력. 모든 필드를 담는 전체 교체라 등록 입력에서 보호자만 뺀 모양이다.
 * 값만 담고, 검증은 엔티티가 한다.
 *
 * @param bodyType 비우려면 null
 * @param preferredFit 비우려면 null
 */
public record ChildProfileUpdateInfo(
    String name,
    LocalDate birthDate,
    Gender gender,
    BigDecimal heightCm,
    BigDecimal weightKg,
    BodyType bodyType,
    PreferredFit preferredFit) {
}
