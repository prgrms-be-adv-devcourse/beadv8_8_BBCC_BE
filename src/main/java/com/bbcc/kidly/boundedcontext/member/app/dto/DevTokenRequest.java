package com.bbcc.kidly.boundedcontext.member.app.dto;

import com.bbcc.kidly.global.security.Role;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record DevTokenRequest(
    @NotNull Long memberId,
    @NotEmpty List<@NotNull Role> roles) {
}
