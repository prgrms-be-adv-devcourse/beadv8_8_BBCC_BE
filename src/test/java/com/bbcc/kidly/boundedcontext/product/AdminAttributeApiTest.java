package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 관리자가 상품 항목과 목록 값을 만들고 조회하는 API를 HTTP 요청부터 응답까지 확인한다.
 */
@FunctionalTest
class AdminAttributeApiTest {

    private static final String PATH = "/api/v1/admin/attributes";
    private static final Long ADMIN_ID = 3L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Test
    @DisplayName("항목을 목록 값과 함께 만들면 201과 Location, 넣은 순서대로 ID가 붙은 값을 준다")
    void createAttribute() {
        AtomicReference<String> location = new AtomicReference<>();

        post(tokens.admin(ADMIN_ID), PATH, Map.of(
            "name", "색상",
            "code", "COLOR",
            "inputType", "PALETTE",
            "values", List.of(
                Map.of("name", "네이비", "colorCode", "#2f3e5c"),
                Map.of("name", "그레이", "colorCode", "#9e9e9e"))))
            .expectStatus().isCreated()
            .expectHeader().value(HttpHeaders.LOCATION, location::set)
            .expectBody()
            .jsonPath("$.attributeId").value(id -> assertThat(location.get()).isEqualTo(PATH + "/" + id))
            .jsonPath("$.attributeId").value(id -> assertThat(id).isInstanceOf(String.class))
            .jsonPath("$.name").isEqualTo("색상")
            .jsonPath("$.code").isEqualTo("COLOR")
            .jsonPath("$.inputType").isEqualTo("PALETTE")
            .jsonPath("$.values[*].name").isEqualTo(List.of("네이비", "그레이"))
            .jsonPath("$.values[*].colorCode").isEqualTo(List.of("#2f3e5c", "#9e9e9e"))
            .jsonPath("$.values[0].valueId").value(id -> assertThat(id).isInstanceOf(String.class));
    }

    @Test
    @DisplayName("항목에 목록 값을 더하면 201과 Location, 만든 값을 준다")
    void addValue() {
        String attributeId = createdAttributeId("COLOR", "PALETTE");
        String valuesPath = PATH + "/" + attributeId + "/values";
        AtomicReference<String> location = new AtomicReference<>();

        post(tokens.admin(ADMIN_ID), valuesPath, Map.of("name", "오트밀", "colorCode", "#e3d3bc"))
            .expectStatus().isCreated()
            .expectHeader().value(HttpHeaders.LOCATION, location::set)
            .expectBody()
            .jsonPath("$.valueId").value(id -> assertThat(location.get()).isEqualTo(valuesPath + "/" + id))
            .jsonPath("$.valueId").value(id -> assertThat(id).isInstanceOf(String.class))
            .jsonPath("$.name").isEqualTo("오트밀")
            .jsonPath("$.colorCode").isEqualTo("#e3d3bc");
    }

    @Test
    @DisplayName("항목 목록은 항목과 값 모두 ID 순서이고, 값이 없는 항목은 빈 목록, 색 코드가 없는 값은 null이다")
    void listAttributes() {
        post(tokens.admin(ADMIN_ID), PATH, Map.of("name", "색상", "code", "COLOR", "inputType", "PALETTE",
            "values", List.of(Map.of("name", "네이비", "colorCode", "#2f3e5c"))))
            .expectStatus().isCreated();
        post(tokens.admin(ADMIN_ID), PATH, Map.of("name", "사이즈", "code", "SIZE", "inputType", "LIST",
            "values", List.of(Map.of("name", "110"), Map.of("name", "120"))))
            .expectStatus().isCreated();
        createdAttributeId("FABRIC_COMPOSITION", "TEXT");

        client.get().uri(PATH)
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[*].code").isEqualTo(List.of("COLOR", "SIZE", "FABRIC_COMPOSITION"))
            .jsonPath("$[0].values[*].name").isEqualTo(List.of("네이비"))
            .jsonPath("$[0].values[0].colorCode").isEqualTo("#2f3e5c")
            .jsonPath("$[1].values[*].name").isEqualTo(List.of("110", "120"))
            .jsonPath("$[1].values[0]")
            .value(value -> assertThat((Map<String, Object>) value).containsEntry("colorCode", null))
            .jsonPath("$[2].inputType").isEqualTo("TEXT")
            .jsonPath("$[2].values").isArray()
            .jsonPath("$[2].values").isEmpty();
    }

    // 값 없이 항목 하나를 만들고 그 ID를 돌려준다
    private String createdAttributeId(String code, String inputType) {
        Map<?, ?> body = post(tokens.admin(ADMIN_ID), PATH,
            Map.of("name", code, "code", code, "inputType", inputType, "values", List.of()))
            .expectStatus().isCreated()
            .expectBody(Map.class)
            .returnResult()
            .getResponseBody();
        return (String) body.get("attributeId");
    }

    private RestTestClient.ResponseSpec post(String token, String path, Object body) {
        return client.post().uri(path)
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
