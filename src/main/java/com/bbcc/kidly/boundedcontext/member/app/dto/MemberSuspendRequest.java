package com.bbcc.kidly.boundedcontext.member.app.dto;

import com.bbcc.kidly.boundedcontext.member.domain.MemberSuspension;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param reason 정지 사유. 저장만 하고 응답하지 않는다
 */
public record MemberSuspendRequest(@NotBlank @Size(max = MemberSuspension.REASON_MAX_LENGTH) String reason) {
}
