package com.bbcc.kidly.boundedcontext.member;

import com.bbcc.kidly.boundedcontext.member.domain.ChildProfile;
import com.bbcc.kidly.shared.member.domain.BodyType;
import com.bbcc.kidly.shared.member.domain.Gender;
import com.bbcc.kidly.shared.member.domain.PreferredFit;
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
        return ChildProfile.register(
            memberId, "첫째", BIRTH_DATE, Gender.MALE, HEIGHT, WEIGHT, BodyType.NORMAL, PreferredFit.LOOSE, NOW);
    }
}
