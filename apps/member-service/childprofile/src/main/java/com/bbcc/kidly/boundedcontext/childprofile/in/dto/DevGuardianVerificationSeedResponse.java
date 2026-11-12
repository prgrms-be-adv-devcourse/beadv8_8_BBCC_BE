package com.bbcc.kidly.boundedcontext.childprofile.in.dto;

import com.bbcc.kidly.boundedcontext.childprofile.app.dto.DevGuardianVerificationSeedResult;

/**
 * @param created 이번 요청이 본인인증 기록을 만들었으면 true, 이미 있었으면 false
 */
public record DevGuardianVerificationSeedResponse(Long memberId, boolean created) {

    public static DevGuardianVerificationSeedResponse from(DevGuardianVerificationSeedResult result) {
        return new DevGuardianVerificationSeedResponse(result.memberId(), result.created());
    }
}
