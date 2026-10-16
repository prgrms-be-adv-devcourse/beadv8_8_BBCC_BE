package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 관리자가 실측 항목을 만들고 조회하는 API를 HTTP 요청부터 응답까지 확인한다.
 */
@FunctionalTest
class AdminMeasurementItemApiTest {

    private static final String PATH = "/api/v1/admin/measurement-items";
    private static final Long ADMIN_ID = 3L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Test
    @DisplayName("실측 항목을 만들면 201과 Location, 만든 실측 항목을 준다")
    void createMeasurementItem() {
        AtomicReference<String> location = new AtomicReference<>();

        post(tokens.admin(ADMIN_ID), Map.of("name", "총장", "code", "TOTAL_LENGTH", "unit", "cm"))
            .expectStatus().isCreated()
            .expectHeader().value(HttpHeaders.LOCATION, location::set)
            .expectBody()
            .jsonPath("$.measurementItemId").value(id -> assertThat(location.get()).isEqualTo(PATH + "/" + id))
            .jsonPath("$.measurementItemId").value(id -> assertThat(id).isInstanceOf(String.class))
            .jsonPath("$.name").isEqualTo("총장")
            .jsonPath("$.code").isEqualTo("TOTAL_LENGTH")
            .jsonPath("$.unit").isEqualTo("cm");
    }

    private RestTestClient.ResponseSpec post(String token, Object body) {
        return client.post().uri(PATH)
            .headers(headers -> {
                if (token != null) {
                    headers.setBearerAuth(token);
                }
            })
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange();
    }
}
