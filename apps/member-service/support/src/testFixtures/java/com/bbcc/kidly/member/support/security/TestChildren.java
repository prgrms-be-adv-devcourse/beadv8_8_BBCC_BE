package com.bbcc.kidly.member.support.security;

import com.bbcc.kidly.shared.auth.PrincipalHeaders;
import java.util.function.Consumer;
import org.springframework.http.HttpHeaders;

public final class TestChildren {
    private TestChildren() {
    }

    public static Consumer<HttpHeaders> of(Long accountId, Long guardianId) {
        return headers -> {
            headers.set(PrincipalHeaders.CHILD_ACCOUNT_ID, String.valueOf(accountId));
            headers.set(PrincipalHeaders.CHILD_GUARDIAN_ID, String.valueOf(guardianId));
        };
    }
}
