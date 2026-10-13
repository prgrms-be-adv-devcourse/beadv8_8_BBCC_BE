package com.bbcc.kidly.boundedcontext.member.app.dto;

import com.bbcc.kidly.global.security.Role;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * @param nickname 비어 있으면 "회원" + memberId
 */
public record DevTokenRequest(
    @NotNull Long memberId,
    String nickname,
    @NotEmpty List<@NotNull Role> roles) {
}
