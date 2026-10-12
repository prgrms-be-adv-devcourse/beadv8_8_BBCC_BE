package com.bbcc.kidly.global.auth;

import static com.bbcc.kidly.support.TestApiController.AUTH_PATH;
import static com.bbcc.kidly.support.TestApiController.PRIVATE_ITEM_PATH;
import static com.bbcc.kidly.support.TestApiController.PUBLIC_PATH;

import com.bbcc.kidly.support.FunctionalTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class PublicApiTest {

    @Autowired
    private RestTestClient client;

    @Test
    @DisplayName("@PublicApi가 붙은 API는 토큰 없이 호출할 수 있다")
    void withoutToken() {
        client.get().uri(PUBLIC_PATH)
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    @DisplayName("@PublicApi가 붙은 API는 잘못된 토큰을 보내도 검증하지 않는다")
    void invalidTokenIgnored() {
        client.get().uri(PUBLIC_PATH)
            .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    @DisplayName("@PublicApi가 없는 API는 토큰 없이 호출하면 401이다")
    void notPublicRequiresLogin() {
        client.get().uri(AUTH_PATH)
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("@PublicApi는 선언한 HTTP 메서드에만 적용된다")
    void onlyDeclaredMethod() {
        client.post().uri(PUBLIC_PATH)
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("경로 변수가 있는 @PublicApi도 토큰 없이 호출할 수 있다")
    void pathVariable() {
        client.get().uri("/api/v1/test/items/1")
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    @DisplayName("@PublicApi 경로 패턴과 겹쳐도 @PublicApi가 없는 API는 로그인이 필요하다")
    void overlappingPrivateApi() {
        client.get().uri(PRIVATE_ITEM_PATH)
            .exchange()
            .expectStatus().isUnauthorized();
    }
}
