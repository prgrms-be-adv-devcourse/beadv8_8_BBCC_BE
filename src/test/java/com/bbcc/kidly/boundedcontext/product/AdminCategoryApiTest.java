package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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

    @Test
    @DisplayName("카테고리 목록은 ID 순서의 평면 배열이고, 중간 카테고리는 유형이 null이고 실측 항목이 빈 배열이다")
    void listCategories() {
        String clothingId = createdCategoryId(category("의류", null, null, List.of()));
        String productTypeId = createProductType("의류", "COLOR");
        String totalLength = createMeasurementItem("총장", "TOTAL_LENGTH");
        String topId = createdCategoryId(category("상의", clothingId, productTypeId, List.of(totalLength)));
        String goodsId = createdCategoryId(category("잡화", null, null, List.of()));

        client.get().uri(PATH)
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[*].categoryId").isEqualTo(List.of(clothingId, topId, goodsId))
            .jsonPath("$[*].name").isEqualTo(List.of("의류", "상의", "잡화"))
            .jsonPath("$[0].parentId").value(id -> assertThat(id).isNull())
            .jsonPath("$[0].leaf").isEqualTo(false)
            .jsonPath("$[0].productTypeId").value(id -> assertThat(id).isNull())
            .jsonPath("$[0].productTypeName").value(name -> assertThat(name).isNull())
            .jsonPath("$[0].measurementItems").isEmpty()
            .jsonPath("$[1].parentId").isEqualTo(clothingId)
            .jsonPath("$[1].leaf").isEqualTo(true)
            .jsonPath("$[1].productTypeId").isEqualTo(productTypeId)
            .jsonPath("$[1].productTypeName").isEqualTo("의류")
            .jsonPath("$[1].measurementItems[*].code").isEqualTo(List.of("TOTAL_LENGTH"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    @DisplayName("카테고리 생성 요청이 잘못되면 400과 ProblemDetail을 준다")
    void rejectInvalidCategory(Map<String, Object> body) {
        assertProblem(post(tokens.admin(ADMIN_ID), body), HttpStatus.BAD_REQUEST);
    }

    // 요청 검증에서 막히므로 부모 · 유형 · 실측 항목 ID는 실제로 없는 값이어도 된다
    static Stream<Named<Map<String, Object>>> invalidRequests() {
        return Stream.of(
            Named.of("이름이 빔", category("", null, null, List.of())),
            Named.of("이름이 255자 초과", category("가".repeat(256), null, null, List.of())),
            Named.of("실측 항목 목록이 없음", category("상의", null, "1", null)),
            Named.of("실측 항목 목록에 null", category("상의", null, "1", Arrays.asList((String) null))),
            Named.of("중간 카테고리에 실측 항목", category("의류", null, null, List.of("1"))));
    }

    @Test
    @DisplayName("TYPE-01 말단 카테고리 아래에 카테고리를 만들면 400을 준다")
    void rejectCategoryUnderLeaf() {
        String productTypeId = createProductType("의류", "COLOR");
        String topId = createdCategoryId(category("상의", null, productTypeId, List.of()));

        RestTestClient.ResponseSpec response = post(tokens.admin(ADMIN_ID), category("반팔", topId, null, List.of()));

        assertProblem(response, HttpStatus.BAD_REQUEST);
        response.expectBody().jsonPath("$.title").isEqualTo("카테고리를 만들 수 없는 위치입니다");
    }

    @Test
    @DisplayName("없는 부모 · 상품 유형 · 실측 항목 ID를 넘기면 404를 준다")
    void rejectUnknownReferences() {
        String productTypeId = createProductType("의류", "COLOR");

        RestTestClient.ResponseSpec unknownParent =
            post(tokens.admin(ADMIN_ID), category("의류", "999999", null, List.of()));
        RestTestClient.ResponseSpec unknownType =
            post(tokens.admin(ADMIN_ID), category("상의", null, "999999", List.of()));
        RestTestClient.ResponseSpec unknownItem =
            post(tokens.admin(ADMIN_ID), category("상의", null, productTypeId, List.of("999999")));

        assertProblem(unknownParent, HttpStatus.NOT_FOUND);
        unknownParent.expectBody().jsonPath("$.title").isEqualTo("카테고리를 찾을 수 없습니다");
        assertProblem(unknownType, HttpStatus.NOT_FOUND);
        unknownType.expectBody().jsonPath("$.title").isEqualTo("상품 유형을 찾을 수 없습니다");
        assertProblem(unknownItem, HttpStatus.NOT_FOUND);
        unknownItem.expectBody().jsonPath("$.title").isEqualTo("실측 항목을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("같은 부모 아래 같은 이름의 카테고리를 만들면 409를 준다")
    void rejectDuplicateNameUnderSameParent() {
        String clothingId = createdCategoryId(category("의류", null, null, List.of()));
        createdCategoryId(category("아동복", clothingId, null, List.of()));

        assertProblem(post(tokens.admin(ADMIN_ID), category("아동복", clothingId, null, List.of())), HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("최상위 카테고리끼리 같은 이름이면 409를 준다")
    void rejectDuplicateTopLevelName() {
        String productTypeId = createProductType("의류", "COLOR");
        createdCategoryId(category("의류", null, null, List.of()));

        assertProblem(post(tokens.admin(ADMIN_ID), category("의류", null, null, List.of())), HttpStatus.CONFLICT);
        assertProblem(
            post(tokens.admin(ADMIN_ID), category("의류", null, productTypeId, List.of())), HttpStatus.CONFLICT);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonAdminTokens")
    @DisplayName("관리자가 아니면 카테고리를 만들거나 조회할 수 없다")
    void rejectNonAdmin(String role, Function<TestTokens, String> token, HttpStatus status) {
        post(token.apply(tokens), category("의류", null, null, List.of())).expectStatus().isEqualTo(status);
        client.get().uri(PATH)
            .headers(headers -> {
                String value = token.apply(tokens);
                if (value != null) {
                    headers.setBearerAuth(value);
                }
            })
            .exchange()
            .expectStatus().isEqualTo(status);
    }

    static Stream<Arguments> nonAdminTokens() {
        return Stream.of(
            Arguments.of("비로그인은 401", (Function<TestTokens, String>) tokens -> null, HttpStatus.UNAUTHORIZED),
            Arguments.of("구매자는 403", (Function<TestTokens, String>) tokens -> tokens.buyer(1L), HttpStatus.FORBIDDEN),
            Arguments.of("판매자는 403", (Function<TestTokens, String>) tokens -> tokens.seller(2L), HttpStatus.FORBIDDEN));
    }

    // 실패 응답이 ProblemDetail 5필드를 담은 application/problem+json인지 확인한다
    private void assertProblem(RestTestClient.ResponseSpec response, HttpStatus status) {
        response.expectStatus().isEqualTo(status)
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.type").exists()
            .jsonPath("$.title").exists()
            .jsonPath("$.status").isEqualTo(status.value())
            .jsonPath("$.detail").exists()
            .jsonPath("$.instance").exists();
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
