package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
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
 * 관리자가 카테고리를 만들고 조회하는 API를 HTTP 요청부터 응답까지 확인한다.
 * 말단 카테고리가 가리킬 상품 유형과 실측 항목은 관리자 API로 만든다.
 */
@FunctionalTest
class AdminCategoryApiTest {

    private static final String PATH = "/api/v1/admin/categories";
    private static final Long ADMIN_ID = 3L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Test
    @DisplayName("최상위 중간 카테고리를 만들면 201과 Location, 유형과 실측 항목이 없는 카테고리를 준다")
    void createTopLevelMiddleCategory() {
        AtomicReference<String> location = new AtomicReference<>();

        post(tokens.admin(ADMIN_ID), category("의류", null, null, List.of()))
            .expectStatus().isCreated()
            .expectHeader().value(HttpHeaders.LOCATION, location::set)
            .expectBody()
            .jsonPath("$.categoryId").value(id -> assertThat(location.get()).isEqualTo(PATH + "/" + id))
            .jsonPath("$.categoryId").value(id -> assertThat(id).isInstanceOf(String.class))
            .jsonPath("$.name").isEqualTo("의류")
            .jsonPath("$.parentId").value(id -> assertThat(id).isNull())
            .jsonPath("$.leaf").isEqualTo(false)
            .jsonPath("$.productTypeId").value(id -> assertThat(id).isNull())
            .jsonPath("$.productTypeName").value(name -> assertThat(name).isNull())
            .jsonPath("$.measurementItems").isEmpty();
    }

    @Test
    @DisplayName("말단 카테고리를 만들면 부모와 상품 유형, 넘긴 순서의 필수 실측 항목을 준다")
    void createLeafCategory() {
        String parentId = createdCategoryId(category("의류", null, null, List.of()));
        String productTypeId = createProductType("의류", "COLOR");
        String totalLength = createMeasurementItem("총장", "TOTAL_LENGTH");
        String shoulderWidth = createMeasurementItem("어깨너비", "SHOULDER_WIDTH");

        // 실측 항목은 ID 순서와 다르게 어깨너비를 먼저 넘긴다
        post(tokens.admin(ADMIN_ID), category("상의", parentId, productTypeId, List.of(shoulderWidth, totalLength)))
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.name").isEqualTo("상의")
            .jsonPath("$.parentId").isEqualTo(parentId)
            .jsonPath("$.leaf").isEqualTo(true)
            .jsonPath("$.productTypeId").isEqualTo(productTypeId)
            .jsonPath("$.productTypeName").isEqualTo("의류")
            .jsonPath("$.measurementItems[*].measurementItemId").isEqualTo(List.of(shoulderWidth, totalLength))
            .jsonPath("$.measurementItems[*].code").isEqualTo(List.of("SHOULDER_WIDTH", "TOTAL_LENGTH"))
            .jsonPath("$.measurementItems[0].name").isEqualTo("어깨너비")
            .jsonPath("$.measurementItems[0].unit").isEqualTo("cm");
    }

    private String createdCategoryId(Map<String, Object> body) {
        return createdId(post(tokens.admin(ADMIN_ID), body), "categoryId");
    }

    // 항목 코드가 겹치지 않게 유형마다 다른 코드를 받는다. 그 항목의 값을 모두 허용하는 필수 옵션 항목 하나로 유형을 만든다
    private String createProductType(String name, String attributeCode) {
        String attributeId = createdId(postTo("/api/v1/admin/attributes", Map.of(
            "name", attributeCode, "code", attributeCode, "inputType", "LIST",
            "values", List.of(Map.of("name", "네이비")))), "attributeId");
        Map<String, Object> link = new HashMap<>();
        link.put("attributeId", attributeId);
        link.put("role", "OPTION");
        link.put("required", true);
        link.put("allValuesAllowed", true);
        link.put("sizeRole", false);
        link.put("priceAffecting", false);
        link.put("allowedValueIds", List.of());
        return createdId(postTo("/api/v1/admin/product-types", Map.of("name", name, "attributes", List.of(link))),
            "productTypeId");
    }

    private String createMeasurementItem(String name, String code) {
        return createdId(postTo("/api/v1/admin/measurement-items", Map.of("name", name, "code", code, "unit", "cm")),
            "measurementItemId");
    }

    // 만들기 응답에서 ID 칸을 읽는다
    private static String createdId(RestTestClient.ResponseSpec response, String idField) {
        Map<?, ?> body = response.expectStatus().isCreated()
            .expectBody(Map.class)
            .returnResult()
            .getResponseBody();
        return (String) body.get(idField);
    }

    private RestTestClient.ResponseSpec postTo(String path, Object body) {
        return client.post().uri(path)
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange();
    }

    private static Map<String, Object> category(
        String name, String parentId, String productTypeId, List<String> measurementItemIds) {
        Map<String, Object> category = new HashMap<>();
        category.put("name", name);
        category.put("parentId", parentId);
        category.put("productTypeId", productTypeId);
        category.put("measurementItemIds", measurementItemIds);
        return category;
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
