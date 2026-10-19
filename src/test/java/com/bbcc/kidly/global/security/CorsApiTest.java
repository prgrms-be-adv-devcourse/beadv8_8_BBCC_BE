package com.bbcc.kidly.global.security;

import static com.bbcc.kidly.support.TestApiController.PUBLIC_PATH;

import com.bbcc.kidly.support.FunctionalTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class CorsApiTest {

    private static final String FRONT_ORIGIN = "http://localhost:5173";
    private static final String UUID_REGEX = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Autowired
    private RestTestClient client;

    @Test
    @DisplayName("허용된 origin의 요청에 X-Request-Id를 프론트가 읽을 수 있게 노출한다")
    void exposesRequestId() {
        client.get().uri(PUBLIC_PATH)
            .header(HttpHeaders.ORIGIN, FRONT_ORIGIN)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONT_ORIGIN)
            .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "X-Request-Id")
            .expectHeader().valueMatches("X-Request-Id", UUID_REGEX);
    }
}
