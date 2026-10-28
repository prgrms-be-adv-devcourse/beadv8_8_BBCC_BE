package com.bbcc.kidly.boundedcontext.member.app.dto;

import com.bbcc.kidly.boundedcontext.member.domain.MemberPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param reason 정지 사유. 저장만 하고 응답하지 않는다
 */
public record MemberSuspendRequest(@NotBlank @Size(max = MemberPolicy.SUSPEND_REASON_MAX_LENGTH) String reason) {
}
