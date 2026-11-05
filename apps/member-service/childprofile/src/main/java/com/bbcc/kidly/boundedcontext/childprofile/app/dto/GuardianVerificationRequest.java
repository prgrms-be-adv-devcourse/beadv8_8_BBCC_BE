package com.bbcc.kidly.boundedcontext.childprofile.app.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param identityVerificationId 프론트가 포트원 본인인증을 요청할 때 만든 ID. 서버가 이 ID로 포트원에 결과를 다시 조회한다
 */
public record GuardianVerificationRequest(@NotBlank String identityVerificationId) {
}
