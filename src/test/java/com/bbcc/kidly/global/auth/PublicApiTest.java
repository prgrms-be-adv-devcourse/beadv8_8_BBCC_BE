package com.bbcc.kidly.global.auth;

import static com.bbcc.kidly.support.TestApiController.AUTH_PATH;
import static com.bbcc.kidly.support.TestApiController.PRIVATE_ITEM_PATH;
import static com.bbcc.kidly.support.TestApiController.PUBLIC_ITEM_PATH;
import static com.bbcc.kidly.support.TestApiController.PUBLIC_PATH;

import com.bbcc.kidly.boundedcontext.member.out.AccessTokenIssuer;
import com.bbcc.kidly.support.FunctionalTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class PublicApiTest {

    @Autowired
    private RestTestClient client;

    @Autowired
    private AccessTokenIssuer accessTokenIssuer;

    @Test
    @DisplayName("@PublicApi가 붙은 API는 토큰 없이 호출할 수 있다")
    void withoutToken() {
        client.get().uri(PUBLIC_PATH)
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    @DisplayName("@PublicApi가 붙은 API도 유효한 토큰을 보내면 로그인 상태로 호출된다")
    void validTokenAuthenticated() {
        client.get().uri(PUBLIC_PATH)
            .headers(headers -> headers.setBearerAuth(accessTokenIssuer.issue(1L, List.of("BUYER"))))
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    @DisplayName("@PublicApi가 붙은 API도 잘못된 토큰을 보내면 401이다")
    void invalidTokenRejected() {
        client.get().uri(PUBLIC_PATH)
            .headers(headers -> headers.setBearerAuth("not-a-jwt"))
            .exchange()
            .expectStatus().isUnauthorized();
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
        client.get().uri(PUBLIC_ITEM_PATH, 1)
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
