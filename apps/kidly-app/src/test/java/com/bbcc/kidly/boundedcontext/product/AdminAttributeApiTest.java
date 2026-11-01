package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProblemDetailAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.support.FunctionalTest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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

    @Test
    @DisplayName("항목을 목록 값과 함께 만들면 201과 Location, 넣은 순서대로 ID가 붙은 값을 준다")
    void createAttribute() {
        AtomicReference<String> location = new AtomicReference<>();

        post(TestMembers.admin(ADMIN_ID), PATH, Map.of(
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

        post(TestMembers.admin(ADMIN_ID), valuesPath, Map.of("name", "오트밀", "colorCode", "#e3d3bc"))
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
        post(TestMembers.admin(ADMIN_ID), PATH, Map.of("name", "색상", "code", "COLOR", "inputType", "PALETTE",
            "values", List.of(Map.of("name", "네이비", "colorCode", "#2f3e5c"))))
            .expectStatus().isCreated();
        post(TestMembers.admin(ADMIN_ID), PATH, Map.of("name", "사이즈", "code", "SIZE", "inputType", "LIST",
            "values", List.of(Map.of("name", "110"), Map.of("name", "120"))))
            .expectStatus().isCreated();
        createdAttributeId("FABRIC_COMPOSITION", "TEXT");

        client.get().uri(PATH)
            .headers(TestMembers.admin(ADMIN_ID))
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidAttributeRequests")
    @DisplayName("항목 생성 요청이 잘못되면 400과 ProblemDetail을 준다")
    void rejectInvalidAttribute(Map<String, Object> body) {
        assertProblem(post(TestMembers.admin(ADMIN_ID), PATH, body), HttpStatus.BAD_REQUEST);
    }

    static Stream<Named<Map<String, Object>>> invalidAttributeRequests() {
        return Stream.of(
            Named.of("이름이 빔", attribute("", "COLOR", "PALETTE", List.of())),
            Named.of("이름이 255자 초과", attribute("가".repeat(256), "COLOR", "PALETTE", List.of())),
            Named.of("코드가 빔", attribute("색상", "", "PALETTE", List.of())),
            Named.of("코드에 소문자", attribute("색상", "color", "PALETTE", List.of())),
            Named.of("코드가 255자 초과", attribute("색상", "C".repeat(256), "PALETTE", List.of())),
            Named.of("없는 입력 방식", attribute("색상", "COLOR", "COLOUR", List.of())),
            Named.of("목록 값 없음(null)", Map.of("name", "색상", "code", "COLOR", "inputType", "PALETTE")),
            Named.of("목록 값 이름이 빔", attribute("색상", "COLOR", "PALETTE", List.of(Map.of("name", "")))),
            Named.of("색 코드 형식", attribute("색상", "COLOR", "PALETTE",
                List.of(Map.of("name", "레드", "colorCode", "red")))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidValueRequests")
    @DisplayName("목록 값 추가 요청이 잘못되면 400과 ProblemDetail을 준다")
    void rejectInvalidValue(Map<String, Object> body) {
        String attributeId = createdAttributeId("COLOR", "PALETTE");

        assertProblem(post(TestMembers.admin(ADMIN_ID), PATH + "/" + attributeId + "/values", body),
            HttpStatus.BAD_REQUEST);
    }

    static Stream<Named<Map<String, Object>>> invalidValueRequests() {
        return Stream.of(
            Named.of("이름이 빔", Map.of("name", "")),
            Named.of("이름이 255자 초과", Map.of("name", "가".repeat(256))),
            Named.of("색 코드 형식", Map.of("name", "레드", "colorCode", "#ff00")));
    }

    @Test
    @DisplayName("없는 항목에 목록 값을 더하면 404와 ProblemDetail을 준다")
    void rejectValueOfMissingAttribute() {
        assertProblem(post(TestMembers.admin(ADMIN_ID), PATH + "/999/values", Map.of("name", "레드")),
            HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("같은 코드의 항목을 만들면 409와 ProblemDetail을 준다")
    void rejectDuplicateAttributeCode() {
        createdAttributeId("COLOR", "PALETTE");

        assertProblem(post(TestMembers.admin(ADMIN_ID), PATH, attribute("색상2", "COLOR", "PALETTE", List.of())),
            HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("같은 항목에 같은 이름의 목록 값을 더하면 409와 ProblemDetail을 준다")
    void rejectDuplicateValueName() {
        String attributeId = createdAttributeId("COLOR", "PALETTE");
        String valuesPath = PATH + "/" + attributeId + "/values";
        post(TestMembers.admin(ADMIN_ID), valuesPath, Map.of("name", "네이비")).expectStatus().isCreated();

        assertProblem(post(TestMembers.admin(ADMIN_ID), valuesPath, Map.of("name", "네이비")), HttpStatus.CONFLICT);
    }

    private static Map<String, Object> attribute(String name, String code, String inputType, List<?> values) {
        return Map.of("name", name, "code", code, "inputType", inputType, "values", values);
    }

    // 값 없이 항목 하나를 만들고 그 ID를 돌려준다
    private String createdAttributeId(String code, String inputType) {
        Map<?, ?> body = post(TestMembers.admin(ADMIN_ID), PATH,
            Map.of("name", code, "code", code, "inputType", inputType, "values", List.of()))
            .expectStatus().isCreated()
            .expectBody(Map.class)
            .returnResult()
            .getResponseBody();
        return (String) body.get("attributeId");
    }

    private RestTestClient.ResponseSpec post(Consumer<HttpHeaders> login, String path, Object body) {
        return client.post().uri(path)
            .headers(login)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange();
    }
}
