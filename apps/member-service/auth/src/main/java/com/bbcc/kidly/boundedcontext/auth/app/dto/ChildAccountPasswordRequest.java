package com.bbcc.kidly.boundedcontext.auth.app.dto;

import jakarta.validation.constraints.NotBlank;

public record ChildAccountPasswordRequest(@NotBlank String password) {
}
