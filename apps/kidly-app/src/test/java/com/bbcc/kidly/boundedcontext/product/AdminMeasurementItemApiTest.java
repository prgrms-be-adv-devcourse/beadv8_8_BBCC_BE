package com.bbcc.kidly.boundedcontext.product;

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
 * 관리자가 실측 항목을 만들고 조회하는 API를 HTTP 요청부터 응답까지 확인한다.
 */
@FunctionalTest
class AdminMeasurementItemApiTest {

    private static final String PATH = "/api/v1/admin/measurement-items";
    private static final Long ADMIN_ID = 3L;

    @Autowired
    private RestTestClient client;

    @Test
    @DisplayName("실측 항목을 만들면 201과 Location, 만든 실측 항목을 준다")
    void createMeasurementItem() {
        AtomicReference<String> location = new AtomicReference<>();

        post(TestMembers.admin(ADMIN_ID), Map.of("name", "총장", "code", "TOTAL_LENGTH", "unit", "cm"))
            .expectStatus().isCreated()
            .expectHeader().value(HttpHeaders.LOCATION, location::set)
            .expectBody()
            .jsonPath("$.measurementItemId").value(id -> assertThat(location.get()).isEqualTo(PATH + "/" + id))
            .jsonPath("$.measurementItemId").value(id -> assertThat(id).isInstanceOf(String.class))
            .jsonPath("$.name").isEqualTo("총장")
            .jsonPath("$.code").isEqualTo("TOTAL_LENGTH")
            .jsonPath("$.unit").isEqualTo("cm");
    }

    @Test
    @DisplayName("실측 항목 목록은 ID 순서다")
    void listMeasurementItems() {
        post(TestMembers.admin(ADMIN_ID), Map.of("name", "총장", "code", "TOTAL_LENGTH", "unit", "cm"))
            .expectStatus().isCreated();
        post(TestMembers.admin(ADMIN_ID), Map.of("name", "어깨너비", "code", "SHOULDER_WIDTH", "unit", "cm"))
            .expectStatus().isCreated();

        client.get().uri(PATH)
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[*].code").isEqualTo(List.of("TOTAL_LENGTH", "SHOULDER_WIDTH"))
            .jsonPath("$[*].name").isEqualTo(List.of("총장", "어깨너비"))
            .jsonPath("$[0].measurementItemId").value(id -> assertThat(id).isInstanceOf(String.class));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    @DisplayName("실측 항목 생성 요청이 잘못되면 400과 ProblemDetail을 준다")
    void rejectInvalidMeasurementItem(Map<String, Object> body) {
        post(TestMembers.admin(ADMIN_ID), body)
            .expectStatus().isBadRequest()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.type").exists()
            .jsonPath("$.title").exists()
            .jsonPath("$.status").isEqualTo(400)
            .jsonPath("$.detail").exists()
            .jsonPath("$.instance").exists();
    }

    static Stream<Named<Map<String, Object>>> invalidRequests() {
        return Stream.of(
            Named.of("이름이 빔", Map.of("name", "", "code", "TOTAL_LENGTH", "unit", "cm")),
            Named.of("이름이 255자 초과", Map.of("name", "가".repeat(256), "code", "TOTAL_LENGTH", "unit", "cm")),
            Named.of("코드가 빔", Map.of("name", "총장", "code", "", "unit", "cm")),
            Named.of("코드에 소문자", Map.of("name", "총장", "code", "total_length", "unit", "cm")),
            Named.of("코드가 255자 초과", Map.of("name", "총장", "code", "T".repeat(256), "unit", "cm")),
            Named.of("단위가 빔", Map.of("name", "총장", "code", "TOTAL_LENGTH", "unit", "")),
            Named.of("단위가 255자 초과", Map.of("name", "총장", "code", "TOTAL_LENGTH", "unit", "c".repeat(256))));
    }

    @Test
    @DisplayName("같은 코드의 실측 항목을 만들면 409를 준다")
    void rejectDuplicateCode() {
        post(TestMembers.admin(ADMIN_ID), Map.of("name", "총장", "code", "TOTAL_LENGTH", "unit", "cm"))
            .expectStatus().isCreated();

        post(TestMembers.admin(ADMIN_ID), Map.of("name", "기장", "code", "TOTAL_LENGTH", "unit", "cm"))
            .expectStatus().isEqualTo(HttpStatus.CONFLICT)
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    private RestTestClient.ResponseSpec post(Consumer<HttpHeaders> login, Object body) {
        return client.post().uri(PATH)
            .headers(login)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange();
    }
}
