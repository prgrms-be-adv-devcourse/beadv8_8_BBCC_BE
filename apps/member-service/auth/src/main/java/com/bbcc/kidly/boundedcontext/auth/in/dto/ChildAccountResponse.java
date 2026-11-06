package com.bbcc.kidly.boundedcontext.auth.in.dto;

import com.bbcc.kidly.boundedcontext.auth.app.dto.ChildAccountResult;

public record ChildAccountResponse(Long id, Long childProfileId, String loginId) {
    public static ChildAccountResponse from(ChildAccountResult result) {
        return new ChildAccountResponse(result.id(), result.childProfileId(), result.loginId());
    }
}
