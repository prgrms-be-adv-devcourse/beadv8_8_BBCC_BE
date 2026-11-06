package com.bbcc.kidly.boundedcontext.auth.app.dto;

import com.bbcc.kidly.boundedcontext.auth.domain.ChildAccount;

public record ChildAccountResult(Long id, Long childProfileId, String loginId) {
    public static ChildAccountResult from(ChildAccount account) {
        return new ChildAccountResult(account.getId(), account.getChildProfileId(), account.getLoginId());
    }
}
