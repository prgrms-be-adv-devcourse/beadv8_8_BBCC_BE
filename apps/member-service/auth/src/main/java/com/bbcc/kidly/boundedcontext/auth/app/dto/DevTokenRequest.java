package com.bbcc.kidly.boundedcontext.auth.app.dto;

import com.bbcc.kidly.shared.auth.Role;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record DevTokenRequest(
    @NotNull Long memberId,
    String nickname,
    @NotEmpty List<@NotNull Role> roles) {
}
