package com.bbcc.kidly.boundedcontext.childprofile.app.dto;

import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfilePolicy;
import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfileRegisterInfo;
import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfileUpdateInfo;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 자녀 등록 · 수정 요청. 수정은 모든 필드를 담아 보내는 전체 교체다.
 * 키 · 몸무게는 소수 첫째 자리까지만 받는다. 둘째 자리가 있으면 반올림하지 않고 400이다.
 *
 * @param bodyType 비우면 null
 * @param preferredFit 비우면 null
 */
public record ChildProfileRequest(
    @NotBlank @Size(max = ChildProfilePolicy.NAME_MAX_LENGTH) String name,
    @NotNull @PastOrPresent LocalDate birthDate,
    @NotNull Gender gender,
    @NotNull @DecimalMin(ChildProfilePolicy.HEIGHT_CM_MIN) @DecimalMax(ChildProfilePolicy.HEIGHT_CM_MAX)
    @Digits(integer = 3, fraction = 1) BigDecimal heightCm,
    @NotNull @DecimalMin(ChildProfilePolicy.WEIGHT_KG_MIN) @DecimalMax(ChildProfilePolicy.WEIGHT_KG_MAX)
    @Digits(integer = 3, fraction = 1) BigDecimal weightKg,
    BodyType bodyType,
    PreferredFit preferredFit) {

    public ChildProfileRegisterInfo toRegisterInfo(Long memberId) {
        return new ChildProfileRegisterInfo(
            memberId, name, birthDate, gender, heightCm, weightKg, bodyType, preferredFit);
    }

    public ChildProfileUpdateInfo toUpdateInfo() {
        return new ChildProfileUpdateInfo(name, birthDate, gender, heightCm, weightKg, bodyType, preferredFit);
    }
}
