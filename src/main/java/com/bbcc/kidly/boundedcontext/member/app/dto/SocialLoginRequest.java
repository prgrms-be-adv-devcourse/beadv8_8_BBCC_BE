package com.bbcc.kidly.boundedcontext.member.app.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param code 프론트가 소셜 제공자에서 받은 인가 코드
 */
public record SocialLoginRequest(@NotBlank String code) {
}
