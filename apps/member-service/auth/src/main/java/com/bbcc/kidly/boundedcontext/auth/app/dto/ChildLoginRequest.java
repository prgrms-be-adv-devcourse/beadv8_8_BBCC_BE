package com.bbcc.kidly.boundedcontext.auth.app.dto;

import jakarta.validation.constraints.NotBlank;

public record ChildLoginRequest(@NotBlank String loginId, @NotBlank String password) {
}
