package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import java.util.ArrayList;
import java.util.HashMap;
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
 * 관리자가 상품 유형을 만들고 조회하는 API를 HTTP 요청부터 응답까지 확인한다.
 * 유형에 연결할 항목과 목록 값은 관리자 항목 API로 만든다.
 */
@FunctionalTest
class AdminProductTypeApiTest {

    private static final String PATH = "/api/v1/admin/product-types";
    private static final String ATTRIBUTES_PATH = "/api/v1/admin/attributes";
    private static final Long ADMIN_ID = 3L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Test
    @DisplayName("유형을 만들면 201과 Location, 넘긴 순서의 연결과 ID 순서의 허용 값을 준다")
    void createProductType() {
        CreatedAttribute color = createAttribute("COLOR", "PALETTE", List.of("네이비", "그레이", "오트밀"));
        CreatedAttribute size = createAttribute("SIZE", "LIST", List.of("110", "120"));
        AtomicReference<String> location = new AtomicReference<>();

        // 연결은 항목 ID 순서와 다르게 사이즈를 먼저 넘기고, 허용 값은 ID가 큰 오트밀을 먼저 넘긴다
        post(tokens.admin(ADMIN_ID), Map.of(
            "name", "의류",
            "attributes", List.of(
                link(size.id(), "OPTION", true, true, true, false, List.of()),
                link(color.id(), "OPTION", true, false, false, false,
                    List.of(color.valueId("오트밀"), color.valueId("네이비"))))))
            .expectStatus().isCreated()
            .expectHeader().value(HttpHeaders.LOCATION, location::set)
            .expectBody()
            .jsonPath("$.productTypeId").value(id -> assertThat(location.get()).isEqualTo(PATH + "/" + id))
            .jsonPath("$.productTypeId").value(id -> assertThat(id).isInstanceOf(String.class))
            .jsonPath("$.name").isEqualTo("의류")
            .jsonPath("$.attributes[*].code").isEqualTo(List.of("SIZE", "COLOR"))
            .jsonPath("$.attributes[0].attributeId").isEqualTo(size.id())
            .jsonPath("$.attributes[0].name").isEqualTo("SIZE")
            .jsonPath("$.attributes[0].inputType").isEqualTo("LIST")
            .jsonPath("$.attributes[0].role").isEqualTo("OPTION")
            .jsonPath("$.attributes[0].required").isEqualTo(true)
            .jsonPath("$.attributes[0].allValuesAllowed").isEqualTo(true)
            .jsonPath("$.attributes[0].sizeRole").isEqualTo(true)
            .jsonPath("$.attributes[0].priceAffecting").isEqualTo(false)
            .jsonPath("$.attributes[0].allowedValues").isEmpty()
            .jsonPath("$.attributes[1].allValuesAllowed").isEqualTo(false)
            .jsonPath("$.attributes[1].sizeRole").isEqualTo(false)
            .jsonPath("$.attributes[1].allowedValues[*].name").isEqualTo(List.of("네이비", "오트밀"))
            .jsonPath("$.attributes[1].allowedValues[*].valueId")
            .isEqualTo(List.of(color.valueId("네이비"), color.valueId("오트밀")))
            .jsonPath("$.attributes[1].allowedValues[*].colorCode").isEqualTo(List.of("#000001", "#000003"));
    }

    // 유형에 연결할 항목 하나의 설정이다
    private static Map<String, Object> link(String attributeId, String role, boolean required,
        boolean allValuesAllowed, boolean sizeRole, boolean priceAffecting, List<String> allowedValueIds) {
        Map<String, Object> link = new HashMap<>();
        link.put("attributeId", attributeId);
        link.put("role", role);
        link.put("required", required);
        link.put("allValuesAllowed", allValuesAllowed);
        link.put("sizeRole", sizeRole);
        link.put("priceAffecting", priceAffecting);
        link.put("allowedValueIds", allowedValueIds);
        return link;
    }

    /**
     * 관리자 항목 API로 만든 항목과 그 값의 ID다.
     *
     * @param valueIds 값 이름별 값 ID
     */
    private record CreatedAttribute(String id, Map<String, String> valueIds) {

        String valueId(String name) {
            return valueIds.get(name);
        }
    }

    // 항목을 이름이 코드와 같게 만든다. 팔레트 항목의 값에는 넣은 순서대로 색 코드 #000001, #000002 …를 붙인다
    private CreatedAttribute createAttribute(String code, String inputType, List<String> valueNames) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (int i = 0; i < valueNames.size(); i++) {
            Map<String, Object> value = new HashMap<>();
            value.put("name", valueNames.get(i));
            value.put("colorCode", "PALETTE".equals(inputType) ? String.format("#%06d", i + 1) : null);
            values.add(value);
        }
        Map<?, ?> body = client.post().uri(ATTRIBUTES_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("name", code, "code", code, "inputType", inputType, "values", values))
            .exchange()
            .expectStatus().isCreated()
            .expectBody(Map.class)
            .returnResult()
            .getResponseBody();
        Map<String, String> valueIds = new HashMap<>();
        for (Object value : (List<?>) body.get("values")) {
            Map<?, ?> created = (Map<?, ?>) value;
            valueIds.put((String) created.get("name"), (String) created.get("valueId"));
        }
        return new CreatedAttribute((String) body.get("attributeId"), valueIds);
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
