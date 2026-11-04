package com.bbcc.kidly.boundedcontext.auth.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ChildAccountCreateRequest(@NotNull Long childProfileId,
    @NotBlank @Pattern(regexp = "^[a-z0-9]{4,20}$") String loginId, @NotBlank String password) {
}
