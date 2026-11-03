package com.bbcc.kidly.global.security;

import com.bbcc.kidly.shared.auth.PrincipalHeaders;
import java.util.function.Consumer;
import org.springframework.http.HttpHeaders;

public final class TestAdmins {

    private TestAdmins() {
    }

    public static Consumer<HttpHeaders> of(Long adminId) {
        return headers -> headers.set(PrincipalHeaders.ADMIN_ID, String.valueOf(adminId));
    }
}
