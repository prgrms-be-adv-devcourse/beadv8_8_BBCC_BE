package com.bbcc.kidly.boundedcontext.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.auth.app.dto.AdminCreateRequest;
import com.bbcc.kidly.boundedcontext.auth.app.dto.AdminLoginRequest;
import com.bbcc.kidly.boundedcontext.auth.app.dto.AdminResponse;
import com.bbcc.kidly.global.security.TestAdmins;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.support.FunctionalTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class AdminAccountApiTest {

    private static final String PATH = "/api/v1/admin/admins";
    private static final String PASSWORD = "password-12345";

    @Autowired
    private RestTestClient client;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsAdminAndAllowsLogin() {
        var result = client.post().uri(PATH).headers(TestAdmins.of(7L))
            .contentType(MediaType.APPLICATION_JSON).body(new AdminCreateRequest(" New@Kidly.Test ", PASSWORD))
            .exchange().expectStatus().isCreated().returnResult(AdminResponse.class);
        var admin = result.getResponseBody();
        assertThat(admin.email()).isEqualTo("new@kidly.test");
        assertThat(result.getResponseHeaders().getFirst(HttpHeaders.LOCATION)).isEqualTo(PATH + "/" + admin.id());
        assertThat(jdbcTemplate.queryForObject("SELECT created_by FROM auth.admin_account WHERE id = ?",
            Long.class, admin.id())).isEqualTo(7L);
        client.post().uri("/api/v1/auth/admin/login").contentType(MediaType.APPLICATION_JSON)
            .body(new AdminLoginRequest(admin.email(), PASSWORD)).exchange().expectStatus().isOk();
        create(" NEW@KIDLY.TEST ", PASSWORD).expectStatus().isEqualTo(409);
    }

    @Test
    void validatesInputAndRequiresAdmin() {
        create("test@kidly.test", "12345678901").expectStatus().isBadRequest();
        create("test@kidly.test", "a".repeat(73)).expectStatus().isBadRequest();
        create("invalid", PASSWORD).expectStatus().isBadRequest();
        client.post().uri(PATH).headers(TestMembers.buyer(1L)).contentType(MediaType.APPLICATION_JSON)
            .body(new AdminCreateRequest("test@kidly.test", PASSWORD)).exchange().expectStatus().isUnauthorized();
    }

    private RestTestClient.ResponseSpec create(String email, String password) {
        return client.post().uri(PATH).headers(TestAdmins.of(7L)).contentType(MediaType.APPLICATION_JSON)
            .body(new AdminCreateRequest(email, password)).exchange();
    }
}
