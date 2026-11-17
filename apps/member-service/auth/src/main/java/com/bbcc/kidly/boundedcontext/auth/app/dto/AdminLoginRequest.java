package com.bbcc.kidly.boundedcontext.auth.app.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminLoginRequest(@NotBlank String email, @NotBlank String password) {
}
