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
