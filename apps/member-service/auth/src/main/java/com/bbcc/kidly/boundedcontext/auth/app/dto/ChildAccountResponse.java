package com.bbcc.kidly.boundedcontext.auth.app.dto;

import com.bbcc.kidly.boundedcontext.auth.domain.ChildAccount;

public record ChildAccountResponse(Long id, Long childProfileId, String loginId) {
    public static ChildAccountResponse from(ChildAccount account) {
        return new ChildAccountResponse(account.getId(), account.getChildProfileId(), account.getLoginId());
    }
}
