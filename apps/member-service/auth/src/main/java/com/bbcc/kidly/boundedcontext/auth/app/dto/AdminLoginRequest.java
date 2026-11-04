package com.bbcc.kidly.boundedcontext.auth.app.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param email 앞뒤 공백 · 대소문자는 무시한다
 */
public record AdminLoginRequest(@NotBlank String email, @NotBlank String password) {
}
