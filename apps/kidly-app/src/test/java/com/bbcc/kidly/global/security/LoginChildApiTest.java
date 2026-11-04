package com.bbcc.kidly.global.security;

import com.bbcc.kidly.support.FunctionalTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class LoginChildApiTest {
    private static final String PATH = "/api/v1/test/login-child";

    @Autowired
    private RestTestClient client;

    @Test
    void resolvesChild() {
        client.get().uri(PATH).headers(TestChildren.of(7L, 8L)).exchange()
            .expectStatus().isOk().expectBody(AuthChild.class).isEqualTo(new AuthChild(7L, 8L));
    }

    @Test
    void rejectsOtherPrincipals() {
        client.get().uri(PATH).exchange().expectStatus().isUnauthorized();
        client.get().uri(PATH).headers(TestMembers.buyer(7L)).exchange().expectStatus().isUnauthorized();
        client.get().uri(PATH).headers(TestAdmins.of(7L)).exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/v1/test/login-member").headers(TestChildren.of(7L, 8L)).exchange()
            .expectStatus().isUnauthorized();
    }
}
