package com.bbcc.kidly.boundedcontext.childprofile.app.dto;

import com.bbcc.kidly.boundedcontext.childprofile.domain.GuardianVerification;
import java.time.LocalDateTime;

public record GuardianVerificationResult(boolean verified, LocalDateTime verifiedAt) {
    public static GuardianVerificationResult from(GuardianVerification verification) {
        return new GuardianVerificationResult(true, verification.getVerifiedAt());
    }
}
