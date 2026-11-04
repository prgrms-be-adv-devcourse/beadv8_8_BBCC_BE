package com.bbcc.kidly.boundedcontext.childprofile;

import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfile;
import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfileRegisterInfo;
import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfileUpdateInfo;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class ChildProfileFixture {

    public static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 13, 9, 0);
    public static final LocalDate BIRTH_DATE = LocalDate.of(2019, 1, 15);
    public static final BigDecimal HEIGHT = new BigDecimal("115.0");
    public static final BigDecimal WEIGHT = new BigDecimal("20.0");

    private ChildProfileFixture() {
    }

    public static ChildProfile child(Long memberId) {
        return ChildProfile.register(registerInfo(memberId), NOW);
    }

    public static ChildProfileRegisterInfo registerInfo(Long memberId) {
        return new ChildProfileRegisterInfo(
            memberId, "첫째", BIRTH_DATE, Gender.MALE, HEIGHT, WEIGHT, BodyType.NORMAL, PreferredFit.LOOSE);
    }

    // 픽스처와 같은 값. 바꿀 필드만 다르게 넣어 쓴다
    public static ChildProfileUpdateInfo updateInfo(String name, BigDecimal heightCm, BigDecimal weightKg,
        BodyType bodyType, PreferredFit preferredFit) {
        return new ChildProfileUpdateInfo(name, BIRTH_DATE, Gender.MALE, heightCm, weightKg, bodyType, preferredFit);
    }
}
