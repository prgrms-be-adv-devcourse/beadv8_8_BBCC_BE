package com.bbcc.kidly.global.security;

import com.bbcc.kidly.shared.auth.PrincipalHeaders;
import com.bbcc.kidly.support.FunctionalTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class LoginAdminApiTest {
    private static final String PATH = "/api/v1/test/login-admin";

    @Autowired
    private RestTestClient client;

    @Test
    void resolvesAdmin() {
        client.get().uri(PATH).headers(TestAdmins.of(7L)).exchange()
            .expectStatus().isOk().expectBody(AuthAdmin.class).isEqualTo(new AuthAdmin(7L));
    }

    @Test
    void rejectsMissingMemberAndInvalidHeaders() {
        client.get().uri(PATH).exchange().expectStatus().isUnauthorized();
        client.get().uri(PATH).headers(TestMembers.buyer(7L)).exchange().expectStatus().isUnauthorized();
        client.get().uri(PATH).header(PrincipalHeaders.ADMIN_ID, "abc").exchange().expectStatus().isUnauthorized();
    }
}
