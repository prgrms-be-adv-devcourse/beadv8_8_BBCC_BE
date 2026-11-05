package com.bbcc.kidly.boundedcontext.auth.app.dto;

import com.bbcc.kidly.boundedcontext.auth.domain.AdminAccount;
import java.time.LocalDateTime;

public record AdminResponse(Long id, String email, String status, LocalDateTime createdAt) {
    public static AdminResponse from(AdminAccount account) {
        return new AdminResponse(account.getId(), account.getEmail(), account.getStatus().name(),
            account.getCreatedAt());
    }
}
