package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProblemDetailAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import java.util.ArrayList;
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

    @Test
    @DisplayName("유형 목록은 ID 순서이고, 모든 값 허용 연결의 허용 값은 빈 배열이다")
    void listProductTypes() {
        CreatedAttribute color = createAttribute("COLOR", "PALETTE", List.of("네이비", "그레이"));
        CreatedAttribute size = createAttribute("SIZE", "LIST", List.of("110"));
        post(tokens.admin(ADMIN_ID), Map.of("name", "의류", "attributes", List.of(
            link(color.id(), "OPTION", true, false, false, false, List.of(color.valueId("그레이"))),
            link(size.id(), "OPTION", true, true, true, false, List.of()))))
            .expectStatus().isCreated();
        post(tokens.admin(ADMIN_ID), Map.of("name", "잡화", "attributes", List.of(
            link(color.id(), "OPTION", true, true, false, false, List.of()))))
            .expectStatus().isCreated();

        client.get().uri(PATH)
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[*].name").isEqualTo(List.of("의류", "잡화"))
            .jsonPath("$[0].productTypeId").value(id -> assertThat(id).isInstanceOf(String.class))
            .jsonPath("$[0].attributes[*].code").isEqualTo(List.of("COLOR", "SIZE"))
            .jsonPath("$[0].attributes[0].allowedValues[*].name").isEqualTo(List.of("그레이"))
            .jsonPath("$[0].attributes[1].allowedValues").isEmpty()
            .jsonPath("$[1].attributes[*].code").isEqualTo(List.of("COLOR"))
            .jsonPath("$[1].attributes[0].allowedValues").isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    @DisplayName("유형 생성 요청이 잘못되면 400과 ProblemDetail을 준다")
    void rejectInvalidProductType(Map<String, Object> body) {
        assertProblem(post(tokens.admin(ADMIN_ID), body), HttpStatus.BAD_REQUEST);
    }

    // 요청 검증에서 막히므로 항목 ID는 실제로 없는 값이어도 된다
    static Stream<Named<Map<String, Object>>> invalidRequests() {
        Map<String, Object> validLink = link("1", "OPTION", true, true, false, false, List.of());
        return Stream.of(
            Named.of("이름이 빔", type("", List.of(validLink))),
            Named.of("이름이 255자 초과", type("가".repeat(256), List.of(validLink))),
            Named.of("연결 목록이 없음", type("의류", null)),
            Named.of("연결 목록에 null", type("의류", Arrays.asList((Object) null))),
            Named.of("항목 ID가 없음", type("의류", List.of(link(null, "OPTION", true, true, false, false, List.of())))),
            Named.of("역할이 OPTION · INFO가 아님",
                type("의류", List.of(link("1", "SIZE", true, true, false, false, List.of())))),
            Named.of("역할이 없음", type("의류", List.of(link("1", null, true, true, false, false, List.of())))),
            Named.of("허용 값 목록이 없음", type("의류", List.of(link("1", "OPTION", true, true, false, false, null)))),
            Named.of("허용 값 목록에 null",
                type("의류", List.of(link("1", "OPTION", true, false, false, false, Arrays.asList((String) null))))));
    }

    @Test
    @DisplayName("TYPE-02 필수 옵션 항목이 없는 유형을 만들면 400을 준다")
    void rejectTypeWithoutRequiredOption() {
        CreatedAttribute material = createAttribute("MAIN_MATERIAL", "LIST", List.of("면"));
        CreatedAttribute color = createAttribute("COLOR", "PALETTE", List.of("네이비"));

        RestTestClient.ResponseSpec response = post(tokens.admin(ADMIN_ID), type("의류", List.of(
            link(material.id(), "INFO", true, true, false, false, List.of()),
            link(color.id(), "OPTION", false, true, false, false, List.of()))));

        assertProblem(response, HttpStatus.BAD_REQUEST);
        response.expectBody()
            .jsonPath("$.title").isEqualTo("상품 유형 설정이 잘못되었습니다")
            .jsonPath("$.detail").isEqualTo("필수 옵션 항목을 하나 이상 연결해 주세요.");
    }

    @Test
    @DisplayName("TYPE-06 다른 항목의 값을 허용 값으로 넘기면 400을 준다")
    void rejectAllowedValueOfOtherAttribute() {
        CreatedAttribute color = createAttribute("COLOR", "PALETTE", List.of("네이비"));
        CreatedAttribute size = createAttribute("SIZE", "LIST", List.of("110"));

        RestTestClient.ResponseSpec response = post(tokens.admin(ADMIN_ID), type("의류", List.of(
            link(color.id(), "OPTION", true, false, false, false, List.of(size.valueId("110"))),
            link(size.id(), "OPTION", true, true, true, false, List.of()))));

        assertProblem(response, HttpStatus.BAD_REQUEST);
        response.expectBody()
            .jsonPath("$.title").isEqualTo("상품 유형 설정이 잘못되었습니다")
            .jsonPath("$.detail").isEqualTo("허용 값은 그 항목의 목록 값에서 골라 주세요.");
    }

    @Test
    @DisplayName("없는 항목 ID나 없는 값 ID를 넘기면 404를 준다")
    void rejectUnknownAttributeOrValue() {
        CreatedAttribute color = createAttribute("COLOR", "PALETTE", List.of("네이비"));

        RestTestClient.ResponseSpec unknownAttribute = post(tokens.admin(ADMIN_ID), type("의류", List.of(
            link("999999", "OPTION", true, true, false, false, List.of()))));
        RestTestClient.ResponseSpec unknownValue = post(tokens.admin(ADMIN_ID), type("의류", List.of(
            link(color.id(), "OPTION", true, false, false, false, List.of("999999")))));

        assertProblem(unknownAttribute, HttpStatus.NOT_FOUND);
        unknownAttribute.expectBody().jsonPath("$.title").isEqualTo("항목을 찾을 수 없습니다");
        assertProblem(unknownValue, HttpStatus.NOT_FOUND);
        unknownValue.expectBody().jsonPath("$.title").isEqualTo("목록 값을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("같은 이름의 유형을 만들면 409를 준다")
    void rejectDuplicateProductType() {
        CreatedAttribute color = createAttribute("COLOR", "PALETTE", List.of("네이비"));
        Map<String, Object> colorLink = link(color.id(), "OPTION", true, true, false, false, List.of());
        post(tokens.admin(ADMIN_ID), type("의류", List.of(colorLink))).expectStatus().isCreated();

        assertProblem(post(tokens.admin(ADMIN_ID), type("의류", List.of(colorLink))), HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("TYPE-13 한 유형에 같은 항목을 두 번 연결하면 400을 준다")
    void rejectDuplicateLink() {
        CreatedAttribute color = createAttribute("COLOR", "PALETTE", List.of("네이비"));
        Map<String, Object> colorLink = link(color.id(), "OPTION", true, true, false, false, List.of());

        assertProblem(post(tokens.admin(ADMIN_ID), type("잡화", List.of(colorLink, colorLink))), HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonAdminTokens")
    @DisplayName("관리자가 아니면 유형을 만들거나 조회할 수 없다")
    void rejectNonAdmin(String role, Function<TestTokens, String> token, HttpStatus status) {
        Map<String, Object> body = type("의류", List.of(link("1", "OPTION", true, true, false, false, List.of())));
        post(token.apply(tokens), body).expectStatus().isEqualTo(status);
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

    private static Map<String, Object> type(String name, List<?> attributes) {
        Map<String, Object> type = new HashMap<>();
        type.put("name", name);
        type.put("attributes", attributes);
        return type;
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
