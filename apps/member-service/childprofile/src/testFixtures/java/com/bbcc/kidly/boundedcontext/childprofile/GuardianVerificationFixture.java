package com.bbcc.kidly.boundedcontext.childprofile;

import com.bbcc.kidly.boundedcontext.childprofile.domain.GuardianVerification;
import com.bbcc.kidly.boundedcontext.childprofile.domain.VerifiedIdentity;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class GuardianVerificationFixture {
    private GuardianVerificationFixture() {
    }

    public static GuardianVerification verification(Long memberId) {
        var identity = new VerifiedIdentity("보호자", "01012345678", LocalDate.of(1990, 1, 1), LocalDateTime.now());
        return GuardianVerification.create(memberId, "identity-" + memberId, "transaction-" + memberId,
            identity, LocalDate.now());
    }
}
