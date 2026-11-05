package com.bbcc.kidly.boundedcontext.auth.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AdminCreateRequest(@NotBlank @Email String email, @NotBlank String password) {
    public AdminCreateRequest {
        if (email != null) {
            email = email.strip();
        }
    }
}
