package com.bbcc.kidly.boundedcontext.childprofile.domain;

import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * {@link ChildProfile#register} 입력. 값만 담고, 검증은 엔티티가 한다.
 *
 * @param memberId 보호자. 등록 뒤에는 바뀌지 않는다
 * @param bodyType 입력하지 않았으면 null
 * @param preferredFit 입력하지 않았으면 null
 */
public record ChildProfileRegisterInfo(
    Long memberId,
    String name,
    LocalDate birthDate,
    Gender gender,
    BigDecimal heightCm,
    BigDecimal weightKg,
    BodyType bodyType,
    PreferredFit preferredFit) {
}
