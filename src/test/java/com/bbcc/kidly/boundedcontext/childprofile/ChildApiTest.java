package com.bbcc.kidly.boundedcontext.childprofile;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.childprofile.app.dto.ChildProfileRequest;
import com.bbcc.kidly.boundedcontext.childprofile.in.dto.ChildResponse;
import com.bbcc.kidly.boundedcontext.childprofile.in.dto.GrowthRecordResponse;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class ChildApiTest {

    private static final String CHILDREN_PATH = "/api/v1/children";
    private static final String CHILD_PATH = "/api/v1/children/{childId}";
    private static final String GROWTH_RECORDS_PATH = "/api/v1/children/{childId}/growth-records";
    private static final Long MEMBER_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 2L;
    private static final ChildProfileRequest REQUEST = new ChildProfileRequest(
        "첫째", LocalDate.of(2019, 1, 15), Gender.MALE, new BigDecimal("115.0"), new BigDecimal("20.0"),
        BodyType.NORMAL, PreferredFit.LOOSE);

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Test
    @DisplayName("자녀를 등록하면 201과 Location, 등록된 자녀를 준다")
    void register() {
        var result = client.post().uri(CHILDREN_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(MEMBER_ID)))
            .contentType(MediaType.APPLICATION_JSON)
            .body(REQUEST)
            .exchange()
            .expectStatus().isCreated()
            .returnResult(ChildResponse.class);

        var child = result.getResponseBody();
        assertThat(result.getResponseHeaders().getFirst(HttpHeaders.LOCATION))
            .isEqualTo(CHILDREN_PATH + "/" + child.id());
        assertThat(child.name()).isEqualTo("첫째");
        assertThat(child.heightCm()).isEqualByComparingTo("115.0");
        assertThat(child.bodyType()).isEqualTo(BodyType.NORMAL);
        assertThat(child.growthRecordCount()).isEqualTo(1);
        assertThat(child.lastRecordedAt()).isNotNull();
    }

    @Test
    @DisplayName("내 자녀 목록을 등록 순으로 준다. 남의 자녀는 보이지 않는다")
    void list() {
        var first = register(MEMBER_ID);
        var second = register(MEMBER_ID);
        register(OTHER_MEMBER_ID);

        var children = client.get().uri(CHILDREN_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(MEMBER_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody(new ParameterizedTypeReference<List<ChildResponse>>() { })
            .returnResult()
            .getResponseBody();

        assertThat(children).extracting(ChildResponse::id).containsExactly(first, second);
    }

    @Test
    @DisplayName("자녀 상세를 준다")
    void get() {
        var childId = register(MEMBER_ID);

        client.get().uri(CHILD_PATH, childId)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(MEMBER_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo(childId)
            .jsonPath("$.name").isEqualTo("첫째")
            .jsonPath("$.gender").isEqualTo("MALE")
            .jsonPath("$.birthDate").isEqualTo("2019-01-15")
            .jsonPath("$.preferredFit").isEqualTo("LOOSE");
    }

    @Test
    @DisplayName("수정하면 바뀐 값을 주고, 키가 바뀌었으면 성장 이력이 늘어난다")
    void update() {
        var childId = register(MEMBER_ID);
        var changed = new ChildProfileRequest("첫째", LocalDate.of(2019, 1, 15), Gender.MALE,
            new BigDecimal("118.0"), new BigDecimal("21.0"), null, null);

        var child = client.patch().uri(CHILD_PATH, childId)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(MEMBER_ID)))
            .contentType(MediaType.APPLICATION_JSON)
            .body(changed)
            .exchange()
            .expectStatus().isOk()
            .returnResult(ChildResponse.class)
            .getResponseBody();

        assertThat(child.heightCm()).isEqualByComparingTo("118.0");
        assertThat(child.bodyType()).isNull();
        assertThat(child.growthRecordCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("삭제하면 204이고, 이후 상세 조회는 404다")
    void delete() {
        var childId = register(MEMBER_ID);

        client.delete().uri(CHILD_PATH, childId)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(MEMBER_ID)))
            .exchange()
            .expectStatus().isNoContent();

        client.get().uri(CHILD_PATH, childId)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(MEMBER_ID)))
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("성장 이력을 오래된 순으로 준다")
    void growthRecords() {
        var childId = register(MEMBER_ID);
        client.patch().uri(CHILD_PATH, childId)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(MEMBER_ID)))
            .contentType(MediaType.APPLICATION_JSON)
            .body(new ChildProfileRequest("첫째", LocalDate.of(2019, 1, 15), Gender.MALE,
                new BigDecimal("118.0"), new BigDecimal("20.0"), BodyType.NORMAL, PreferredFit.LOOSE))
            .exchange()
            .expectStatus().isOk();

        var records = client.get().uri(GROWTH_RECORDS_PATH, childId)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(MEMBER_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody(new ParameterizedTypeReference<List<GrowthRecordResponse>>() { })
            .returnResult()
            .getResponseBody();

        assertThat(records).hasSize(2);
        assertThat(records.get(0).heightCm()).isEqualByComparingTo("115.0");
        assertThat(records.get(1).heightCm()).isEqualByComparingTo("118.0");
        assertThat(records.get(1).bodyType()).isEqualTo(BodyType.NORMAL);
    }

    @Test
    @DisplayName("남의 자녀는 상세 · 수정 · 삭제 · 성장 이력 모두 404다")
    void otherMembersChild() {
        var childId = register(MEMBER_ID);
        var otherToken = tokens.buyer(OTHER_MEMBER_ID);

        client.get().uri(CHILD_PATH, childId)
            .headers(headers -> headers.setBearerAuth(otherToken))
            .exchange()
            .expectStatus().isNotFound()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody().jsonPath("$.title").isEqualTo("찾을 수 없습니다");
        client.patch().uri(CHILD_PATH, childId)
            .headers(headers -> headers.setBearerAuth(otherToken))
            .contentType(MediaType.APPLICATION_JSON)
            .body(REQUEST)
            .exchange()
            .expectStatus().isNotFound();
        client.delete().uri(CHILD_PATH, childId)
            .headers(headers -> headers.setBearerAuth(otherToken))
            .exchange()
            .expectStatus().isNotFound();
        client.get().uri(GROWTH_RECORDS_PATH, childId)
            .headers(headers -> headers.setBearerAuth(otherToken))
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("키가 범위를 벗어나거나 소수 둘째 자리가 있으면 400")
    void invalidHeight() {
        expectInvalid(new ChildProfileRequest("첫째", LocalDate.of(2019, 1, 15), Gender.MALE,
            new BigDecimal("210.0"), new BigDecimal("20.0"), null, null), "heightCm");
        expectInvalid(new ChildProfileRequest("첫째", LocalDate.of(2019, 1, 15), Gender.MALE,
            new BigDecimal("115.25"), new BigDecimal("20.0"), null, null), "heightCm");
        expectInvalid(new ChildProfileRequest("첫째", LocalDate.of(2019, 1, 15), Gender.MALE,
            new BigDecimal("115.00"), new BigDecimal("20.0"), null, null), "heightCm");
    }

    @Test
    @DisplayName("생년월일이 미래이거나 이름이 비어 있거나 50자를 넘으면 400")
    void invalidBirthDateOrName() {
        expectInvalid(new ChildProfileRequest("첫째", LocalDate.now().plusDays(1), Gender.MALE,
            new BigDecimal("115.0"), new BigDecimal("20.0"), null, null), "birthDate");
        expectInvalid(new ChildProfileRequest(" ", LocalDate.of(2019, 1, 15), Gender.MALE,
            new BigDecimal("115.0"), new BigDecimal("20.0"), null, null), "name");
        expectInvalid(new ChildProfileRequest("가".repeat(51), LocalDate.of(2019, 1, 15), Gender.MALE,
            new BigDecimal("115.0"), new BigDecimal("20.0"), null, null), "name");
    }

    @Test
    @DisplayName("모르는 체형 값이면 400")
    void unknownBodyType() {
        client.post().uri(CHILDREN_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(MEMBER_ID)))
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"name\":\"첫째\",\"birthDate\":\"2019-01-15\",\"gender\":\"MALE\",\"heightCm\":115.0,"
                + "\"weightKg\":20.0,\"bodyType\":\"FAT\"}")
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("로그인하지 않으면 401")
    void withoutToken() {
        client.get().uri(CHILDREN_PATH)
            .exchange()
            .expectStatus().isUnauthorized();
    }

    private Long register(Long memberId) {
        return client.post().uri(CHILDREN_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(memberId)))
            .contentType(MediaType.APPLICATION_JSON)
            .body(REQUEST)
            .exchange()
            .expectStatus().isCreated()
            .returnResult(ChildResponse.class)
            .getResponseBody()
            .id();
    }

    // detail에 "필드명: 메시지"가 들어 있다
    private void expectInvalid(ChildProfileRequest request, String field) {
        var body = client.post().uri(CHILDREN_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(MEMBER_ID)))
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .exchange()
            .expectStatus().isBadRequest()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.title").isEqualTo("잘못된 요청입니다")
            .returnResult()
            .getResponseBody();

        assertThat(new String(body, StandardCharsets.UTF_8)).contains(field);
    }
}
