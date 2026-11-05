package com.bbcc.kidly.boundedcontext.childprofile.in.dto;

import com.bbcc.kidly.boundedcontext.childprofile.app.dto.GuardianVerificationResult;
import java.time.LocalDateTime;

/**
 * @param verifiedAt 본인인증한 시각. 인증하지 않았으면 null
 */
public record GuardianVerificationResponse(boolean verified, LocalDateTime verifiedAt) {

    public static GuardianVerificationResponse from(GuardianVerificationResult result) {
        return new GuardianVerificationResponse(result.verified(), result.verifiedAt());
    }
}
