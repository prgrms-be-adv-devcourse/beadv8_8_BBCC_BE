package com.bbcc.kidly.boundedcontext.member.app.dto;

import com.bbcc.kidly.boundedcontext.member.domain.MemberPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param reason 회원에게 알림으로 안내할 정지 사유
 */
public record MemberSuspendRequest(@NotBlank @Size(max = MemberPolicy.SUSPEND_REASON_MAX_LENGTH) String reason) {
}
