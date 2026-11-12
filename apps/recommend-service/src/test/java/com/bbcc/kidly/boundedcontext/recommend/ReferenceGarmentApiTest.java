package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.OTHER_MEMBER_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaChildService;
import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaProductService;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.FunctionalTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class ReferenceGarmentApiTest {

    private static final Long CLOTHING_ID = 30L;

    private static final Long TOP_CATEGORY_ID = 7L;

    private static final Long OUTER_CATEGORY_ID = 8L;

    private static final Long DRESS_CATEGORY_ID = 9L;

    private static final Long BOTTOM_CATEGORY_ID = 70L;

    private static final Long SKIRT_CATEGORY_ID = 71L;

    private static final Long PANTS_CATEGORY_ID = 72L;

    private static final String TOP_BODY =
        "{\"categoryId\": 7, \"measurements\": {\"TOTAL_LENGTH\": 42, \"CHEST_WIDTH\": 34.5}}";

    private static final String BOTTOM_BODY =
        "{\"categoryId\": 70, \"measurements\": {\"TOTAL_LENGTH\": 55.5, \"WAIST_WIDTH\": 26}}";

    private static final Long OTHER_MEMBERS_CHILD_ID = 12L;

    private static final Long DELETED_CHILD_ID = 13L;

    private static final Long UNKNOWN_CHILD_ID = 99L;

    private static final ErrorText REQUIRED_MISSING = new ErrorText("필수 실측이 없습니다",
        "상의 · 아우터 · 원피스는 총장과 가슴단면, 바지 · 치마는 총장과 허리단면을 입력해 주세요.");

    private static final ErrorText UNSUPPORTED_CODE = new ErrorText("이 구분에 없는 실측 항목입니다",
        "상의 · 아우터는 총장 · 가슴단면 · 어깨너비 · 소매길이, 원피스는 총장 · 가슴단면 · 어깨너비, "
            + "바지는 총장 · 허리단면 · 밑위, 치마는 총장 · 허리단면 · 엉덩이단면만 입력할 수 있습니다.");

    private static final ErrorText INVALID_VALUE = new ErrorText("잘못된 실측 값입니다",
        "실측 값은 0cm보다 크고 200cm 이하이며, 소수 첫째 자리까지 입력할 수 있습니다.");

    @Autowired
    private RestTestClient client;

    @Autowired
    private RecommendReplicaChildService replicaChildService;

    @Autowired
    private RecommendReplicaProductService replicaProductService;

    @BeforeEach
    void setUpChildAndCategories() {
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());
        applyProduct(31L, TOP_CATEGORY_ID, "상의");
        applyProduct(32L, BOTTOM_CATEGORY_ID, "하의");
        applyProduct(33L, OUTER_CATEGORY_ID, "아우터");
        applyProduct(34L, DRESS_CATEGORY_ID, "원피스");
        applyProduct(35L, SKIRT_CATEGORY_ID, "치마");
        applyProduct(36L, PANTS_CATEGORY_ID, "바지");
    }

    @Test
    @DisplayName("기준 옷이 없는 자녀의 목록을 조회하면 200과 빈 목록을 받는다")
    void listEmptyWhenNoReferenceGarment() {
        list(MEMBER_1_ID, CHILD_1_ID)
            .expectStatus().isOk()
            .expectBody().json("[]");
    }

    @Test
    @DisplayName("상의 기준 옷을 필수 실측만으로 저장하면 200과 저장한 값을 받고, 목록에 그 상의가 나온다")
    void saveTopWithRequiredMeasurements() {
        Map<String, Object> top = garment("TOP", "7", Map.of("TOTAL_LENGTH", 42.0, "CHEST_WIDTH", 34.5));

        put(MEMBER_1_ID, CHILD_1_ID, "TOP", TOP_BODY)
            .expectStatus().isOk()
            .expectBody(Map.class).isEqualTo(top);
        list(MEMBER_1_ID, CHILD_1_ID)
            .expectStatus().isOk()
            .expectBody(List.class).isEqualTo(List.of(top));
    }

    @Test
    @DisplayName("다섯 칸을 섞인 순서로 저장해도 목록은 상의, 바지, 아우터, 원피스, 치마 순서로 나온다")
    void listInPartOrder() {
        put(MEMBER_1_ID, CHILD_1_ID, "SKIRT", body("71", "\"TOTAL_LENGTH\": 32, \"WAIST_WIDTH\": 24,"
            + " \"HIP_WIDTH\": 33.5")).expectStatus().isOk();
        put(MEMBER_1_ID, CHILD_1_ID, "BOTTOM", BOTTOM_BODY).expectStatus().isOk();
        put(MEMBER_1_ID, CHILD_1_ID, "DRESS", body("9", "\"TOTAL_LENGTH\": 60, \"CHEST_WIDTH\": 31"))
            .expectStatus().isOk();
        put(MEMBER_1_ID, CHILD_1_ID, "TOP", TOP_BODY).expectStatus().isOk();
        put(MEMBER_1_ID, CHILD_1_ID, "OUTER", body("8", "\"TOTAL_LENGTH\": 48, \"CHEST_WIDTH\": 38"))
            .expectStatus().isOk();

        list(MEMBER_1_ID, CHILD_1_ID)
            .expectStatus().isOk()
            .expectBody(List.class).isEqualTo(List.of(
                garment("TOP", "7", Map.of("TOTAL_LENGTH", 42.0, "CHEST_WIDTH", 34.5)),
                garment("BOTTOM", "70", Map.of("TOTAL_LENGTH", 55.5, "WAIST_WIDTH", 26.0)),
                garment("OUTER", "8", Map.of("TOTAL_LENGTH", 48.0, "CHEST_WIDTH", 38.0)),
                garment("DRESS", "9", Map.of("TOTAL_LENGTH", 60.0, "CHEST_WIDTH", 31.0)),
                garment("SKIRT", "71", Map.of("TOTAL_LENGTH", 32.0, "WAIST_WIDTH", 24.0, "HIP_WIDTH", 33.5))));
    }

    @Test
    @DisplayName("같은 구분을 다시 저장하면 새 값이 기존 기준 옷을 교체하고, 빼고 보낸 선택 실측은 사라진다")
    void replaceSamePart() {
        put(MEMBER_1_ID, CHILD_1_ID, "BOTTOM", "{\"categoryId\": 70, \"measurements\": {\"TOTAL_LENGTH\": 55.5,"
            + " \"WAIST_WIDTH\": 26, \"RISE\": 20}}")
            .expectStatus().isOk();

        put(MEMBER_1_ID, CHILD_1_ID, "BOTTOM",
            "{\"categoryId\": 72, \"measurements\": {\"TOTAL_LENGTH\": 58, \"WAIST_WIDTH\": 27.5}}")
            .expectStatus().isOk()
            .expectBody(Map.class)
            .isEqualTo(garment("BOTTOM", "72", Map.of("TOTAL_LENGTH", 58.0, "WAIST_WIDTH", 27.5)));

        list(MEMBER_1_ID, CHILD_1_ID)
            .expectStatus().isOk()
            .expectBody(List.class).isEqualTo(List.of(
                garment("BOTTOM", "72", Map.of("TOTAL_LENGTH", 58.0, "WAIST_WIDTH", 27.5))));
    }

    @Test
    @DisplayName("기준 옷을 지우면 204와 빈 본문을 받고 그 구분만 목록에서 빠지며, 이미 없는 구분을 다시 지워도 204다")
    void deleteIsIdempotent() {
        put(MEMBER_1_ID, CHILD_1_ID, "TOP", TOP_BODY).expectStatus().isOk();
        put(MEMBER_1_ID, CHILD_1_ID, "BOTTOM", BOTTOM_BODY).expectStatus().isOk();

        delete(MEMBER_1_ID, CHILD_1_ID, "TOP").expectStatus().isNoContent().expectBody().isEmpty();
        list(MEMBER_1_ID, CHILD_1_ID)
            .expectStatus().isOk()
            .expectBody(List.class).isEqualTo(List.of(
                garment("BOTTOM", "70", Map.of("TOTAL_LENGTH", 55.5, "WAIST_WIDTH", 26.0))));

        delete(MEMBER_1_ID, CHILD_1_ID, "TOP").expectStatus().isNoContent().expectBody().isEmpty();
    }

    @ParameterizedTest(name = "{0} 기준 옷에 카테고리 {1}")
    @CsvSource({"TOP, 7", "BOTTOM, 70", "BOTTOM, 72", "OUTER, 8", "DRESS, 9", "SKIRT, 71"})
    @DisplayName("FIT-01 상의는 상의, 바지는 하의 · 바지, 아우터는 아우터, 원피스는 원피스, 치마는 치마 카테고리로 저장된다")
    void saveWithMatchingCategory(String part, String categoryId) {
        put(MEMBER_1_ID, CHILD_1_ID, part, body(categoryId, requiredMeasurements(part)))
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$").value(Map.class, body -> assertThat(body)
                .containsEntry("part", part)
                .containsEntry("categoryId", categoryId));
    }

    @ParameterizedTest(name = "{0} 기준 옷에 카테고리 {1}")
    @CsvSource({"TOP, 8", "TOP, 9", "TOP, 70", "BOTTOM, 71", "BOTTOM, 7", "OUTER, 7", "DRESS, 7", "SKIRT, 70",
        "SKIRT, 72"})
    @DisplayName("FIT-01 칸과 다른 종류의 카테고리면 400이고 저장되지 않는다")
    void rejectCategoryOfOtherPart(String part, String categoryId) {
        expectProblem(put(MEMBER_1_ID, CHILD_1_ID, part, body(categoryId, requiredMeasurements(part))),
            400, "구분과 맞지 않는 세부 종류입니다",
            "상의는 상의, 바지는 하의 · 바지, 아우터는 아우터, 원피스는 원피스, 치마는 치마 카테고리에서 골라 주세요.",
            path(CHILD_1_ID) + "/" + part);

        list(MEMBER_1_ID, CHILD_1_ID).expectStatus().isOk().expectBody().json("[]");
    }

    @ParameterizedTest(name = "카테고리 {0}")
    @ValueSource(strings = {"999", "30"})
    @DisplayName("상품 복사본이 없는 카테고리나 말단이 아닌 카테고리(의류)면 400이고 저장되지 않는다")
    void rejectUnknownOrNonLeafCategory(String categoryId) {
        expectProblem(put(MEMBER_1_ID, CHILD_1_ID, "TOP", body(categoryId, requiredMeasurements("TOP"))),
            400, "확인할 수 없는 세부 종류입니다", "기준 옷의 세부 종류를 상품 카테고리에서 다시 골라 주세요.",
            path(CHILD_1_ID) + "/TOP");

        list(MEMBER_1_ID, CHILD_1_ID).expectStatus().isOk().expectBody().json("[]");
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("invalidMeasurements")
    @DisplayName("필수 실측이 빠지거나, 그 구분에 없는 코드나 허용 범위 밖의 값이 있으면 400이고 저장되지 않는다")
    void rejectInvalidMeasurements(String part, String measurements, ErrorText error) {
        expectProblem(put(MEMBER_1_ID, CHILD_1_ID, part, body(categoryIdOf(part), measurements)),
            400, error.title(), error.detail(), path(CHILD_1_ID) + "/" + part);

        list(MEMBER_1_ID, CHILD_1_ID).expectStatus().isOk().expectBody().json("[]");
    }

    static Stream<Arguments> invalidMeasurements() {
        return Stream.of(
            Arguments.of("TOP", "\"TOTAL_LENGTH\": 42", REQUIRED_MISSING),
            Arguments.of("TOP", "", REQUIRED_MISSING),
            Arguments.of("BOTTOM", "\"WAIST_WIDTH\": 26", REQUIRED_MISSING),
            Arguments.of("SKIRT", "\"TOTAL_LENGTH\": 32, \"HIP_WIDTH\": 33", REQUIRED_MISSING),
            Arguments.of("TOP", "\"TOTAL_LENGTH\": 42, \"CHEST_WIDTH\": 34.5, \"RISE\": 20", UNSUPPORTED_CODE),
            Arguments.of("BOTTOM", "\"TOTAL_LENGTH\": 55, \"WAIST_WIDTH\": 26, \"SHOULDER_WIDTH\": 30",
                UNSUPPORTED_CODE),
            Arguments.of("BOTTOM", "\"TOTAL_LENGTH\": 55, \"CHEST_WIDTH\": 26", UNSUPPORTED_CODE),
            Arguments.of("DRESS", "\"TOTAL_LENGTH\": 60, \"CHEST_WIDTH\": 31, \"SLEEVE_LENGTH\": 30",
                UNSUPPORTED_CODE),
            Arguments.of("TOP", "\"TOTAL_LENGTH\": 0, \"CHEST_WIDTH\": 34.5", INVALID_VALUE),
            Arguments.of("TOP", "\"TOTAL_LENGTH\": 42, \"CHEST_WIDTH\": -1", INVALID_VALUE),
            Arguments.of("BOTTOM", "\"TOTAL_LENGTH\": 200.1, \"WAIST_WIDTH\": 26", INVALID_VALUE),
            Arguments.of("BOTTOM", "\"TOTAL_LENGTH\": 55, \"WAIST_WIDTH\": 26.25", INVALID_VALUE),
            Arguments.of("TOP", "\"TOTAL_LENGTH\": 42, \"CHEST_WIDTH\": null", INVALID_VALUE));
    }

    @Test
    @DisplayName("세부 종류 ID를 문자열 숫자로 보내도 숫자와 똑같이 저장되고, 응답의 ID는 문자열이다")
    void saveWithStringCategoryId() {
        put(MEMBER_1_ID, CHILD_1_ID, "TOP", TOP_BODY.replace("\"categoryId\": 7", "\"categoryId\": \"7\""))
            .expectStatus().isOk()
            .expectBody(Map.class).isEqualTo(garment("TOP", "7", Map.of("TOTAL_LENGTH", 42.0, "CHEST_WIDTH", 34.5)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequestBodies")
    @DisplayName("세부 종류 ID가 없거나 1보다 작거나 실측 칸이 없으면 400 잘못된 요청이고 저장되지 않는다")
    void rejectInvalidRequestFields(String body, String detail) {
        expectProblem(put(MEMBER_1_ID, CHILD_1_ID, "TOP", body),
            400, "잘못된 요청입니다", detail, path(CHILD_1_ID) + "/TOP");

        list(MEMBER_1_ID, CHILD_1_ID).expectStatus().isOk().expectBody().json("[]");
    }

    static Stream<Arguments> invalidRequestBodies() {
        String measurements = "\"measurements\": {\"TOTAL_LENGTH\": 42, \"CHEST_WIDTH\": 34.5}";
        String idRequired = "categoryId: 세부 종류 ID가 필요합니다.";
        String idPositive = "categoryId: 세부 종류 ID는 1 이상이어야 합니다.";
        String measurementsRequired = "measurements: 실측을 입력해 주세요.";
        return Stream.of(
            Arguments.of("{" + measurements + "}", idRequired),
            Arguments.of("{\"categoryId\": null, " + measurements + "}", idRequired),
            Arguments.of("{\"categoryId\": 0, " + measurements + "}", idPositive),
            Arguments.of("{\"categoryId\": -7, " + measurements + "}", idPositive),
            Arguments.of("{\"categoryId\": 7}", measurementsRequired),
            Arguments.of("{\"categoryId\": 7, \"measurements\": null}", measurementsRequired));
    }

    @ParameterizedTest(name = "구분 {0}")
    @ValueSource(strings = {"SHOES", "top", "TOPS", "dress", "PANTS"})
    @DisplayName("주소의 칸이 TOP · BOTTOM · OUTER · DRESS · SKIRT가 아니면 저장도 삭제도 400이고 기준 옷은 바뀌지 않는다")
    void rejectUnknownPart(String part) {
        put(MEMBER_1_ID, CHILD_1_ID, "TOP", TOP_BODY).expectStatus().isOk();
        String detail = "기준 옷 구분은 TOP · BOTTOM · OUTER · DRESS · SKIRT 중 하나입니다.";

        expectProblem(put(MEMBER_1_ID, CHILD_1_ID, part, TOP_BODY),
            400, "알 수 없는 기준 옷 구분입니다", detail, path(CHILD_1_ID) + "/" + part);
        expectProblem(delete(MEMBER_1_ID, CHILD_1_ID, part),
            400, "알 수 없는 기준 옷 구분입니다", detail, path(CHILD_1_ID) + "/" + part);

        list(MEMBER_1_ID, CHILD_1_ID)
            .expectStatus().isOk()
            .expectBody(List.class).isEqualTo(List.of(
                garment("TOP", "7", Map.of("TOTAL_LENGTH", 42.0, "CHEST_WIDTH", 34.5))));
    }

    @Test
    @DisplayName("다른 회원의 자녀면 조회 · 저장 · 삭제 모두 403이고 그 자녀의 기준 옷은 바뀌지 않는다")
    void rejectOtherMembersChild() {
        applyChild(OTHER_MEMBERS_CHILD_ID, OTHER_MEMBER_ID, false);
        put(OTHER_MEMBER_ID, OTHER_MEMBERS_CHILD_ID, "TOP", TOP_BODY).expectStatus().isOk();
        String path = path(OTHER_MEMBERS_CHILD_ID);

        expectForbidden(list(MEMBER_1_ID, OTHER_MEMBERS_CHILD_ID), path);
        expectForbidden(put(MEMBER_1_ID, OTHER_MEMBERS_CHILD_ID, "TOP", body("8", requiredMeasurements("TOP"))),
            path + "/TOP");
        expectForbidden(put(MEMBER_1_ID, OTHER_MEMBERS_CHILD_ID, "BOTTOM", BOTTOM_BODY), path + "/BOTTOM");
        expectForbidden(delete(MEMBER_1_ID, OTHER_MEMBERS_CHILD_ID, "TOP"), path + "/TOP");

        list(OTHER_MEMBER_ID, OTHER_MEMBERS_CHILD_ID)
            .expectStatus().isOk()
            .expectBody(List.class).isEqualTo(List.of(
                garment("TOP", "7", Map.of("TOTAL_LENGTH", 42.0, "CHEST_WIDTH", 34.5))));
    }

    @ParameterizedTest(name = "자녀 {0}")
    @ValueSource(longs = {99L, 13L})
    @DisplayName("없는 자녀나 삭제된 자녀면 조회 · 저장 · 삭제 모두 404다")
    void rejectUnknownOrDeletedChild(Long childId) {
        applyChild(DELETED_CHILD_ID, MEMBER_1_ID, true);
        String path = path(childId);

        expectChildNotFound(list(MEMBER_1_ID, childId), path);
        expectChildNotFound(put(MEMBER_1_ID, childId, "TOP", TOP_BODY), path + "/TOP");
        expectChildNotFound(delete(MEMBER_1_ID, childId, "TOP"), path + "/TOP");
    }

    @Test
    @DisplayName("로그인하지 않으면 조회 · 저장 · 삭제 모두 401이고 저장되지 않는다")
    void rejectWithoutLogin() {
        String path = path(CHILD_1_ID);

        expectUnauthorized(client.get().uri(path).exchange(), path);
        expectUnauthorized(client.put().uri(path + "/TOP")
            .contentType(MediaType.APPLICATION_JSON).body(TOP_BODY).exchange(), path + "/TOP");
        expectUnauthorized(client.delete().uri(path + "/TOP").exchange(), path + "/TOP");

        list(MEMBER_1_ID, CHILD_1_ID).expectStatus().isOk().expectBody().json("[]");
    }

    private static Map<String, Object> garment(String part, String categoryId, Map<String, Double> measurements) {
        return Map.of("part", part, "categoryId", categoryId, "measurements", measurements);
    }

    private static String requiredMeasurements(String part) {
        if (List.of("TOP", "OUTER", "DRESS").contains(part)) {
            return "\"TOTAL_LENGTH\": 42, \"CHEST_WIDTH\": 34.5";
        }
        return "\"TOTAL_LENGTH\": 55.5, \"WAIST_WIDTH\": 26";
    }

    private static String categoryIdOf(String part) {
        return switch (part) {
            case "TOP" -> "7";
            case "OUTER" -> "8";
            case "DRESS" -> "9";
            case "SKIRT" -> "71";
            default -> "70";
        };
    }

    private static String body(String categoryId, String measurements) {
        return "{\"categoryId\": " + categoryId + ", \"measurements\": {" + measurements + "}}";
    }

    private static void expectProblem(RestTestClient.ResponseSpec response, int status, String title, String detail,
        String instance) {
        response.expectStatus().isEqualTo(status)
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.type").isEqualTo("about:blank")
            .jsonPath("$.title").isEqualTo(title)
            .jsonPath("$.status").isEqualTo(status)
            .jsonPath("$.detail").isEqualTo(detail)
            .jsonPath("$.instance").isEqualTo(instance);
    }

    private static void expectForbidden(RestTestClient.ResponseSpec response, String instance) {
        expectProblem(response, 403, "자녀에 접근할 수 없습니다", "로그인한 보호자의 자녀만 다룰 수 있습니다.", instance);
    }

    private static void expectUnauthorized(RestTestClient.ResponseSpec response, String instance) {
        expectProblem(response, 401, "로그인이 필요합니다", "로그인 후 다시 시도해 주세요.", instance);
    }

    private static void expectChildNotFound(RestTestClient.ResponseSpec response, String instance) {
        expectProblem(response, 404, "자녀를 찾을 수 없습니다", "자녀를 찾을 수 없습니다.", instance);
    }

    private static String path(Long childId) {
        return "/api/v1/recommendations/children/" + childId + "/reference-garments";
    }

    private RestTestClient.ResponseSpec list(Long memberId, Long childId) {
        return client.get().uri(path(childId))
            .headers(TestMembers.buyer(memberId))
            .exchange();
    }

    private RestTestClient.ResponseSpec put(Long memberId, Long childId, String part, String body) {
        return client.put().uri(path(childId) + "/" + part)
            .headers(TestMembers.buyer(memberId))
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange();
    }

    private void applyChild(Long childId, Long memberId, boolean deleted) {
        replicaChildService.applyChildChanged(new ChildChangedEvent(UUID.randomUUID(),
            RecommendFixture.CHILD_CHANGED_AT, new ChildChangedEvent.Data(childId, memberId,
            new BigDecimal("115.0"), new BigDecimal("20.0"), Gender.MALE, RecommendFixture.CHILD_1_BIRTH_DATE,
            BodyType.NORMAL, PreferredFit.LOOSE, deleted)));
    }

    private void applyProduct(Long productId, Long leafCategoryId, String leafCategoryName) {
        ProductChangedEvent.Data data = RecommendFixture.productAChangedEvent().data();
        List<ProductChangedEvent.CategoryPathEntry> categoryPath = List.of(
            new ProductChangedEvent.CategoryPathEntry(CLOTHING_ID, "의류"),
            new ProductChangedEvent.CategoryPathEntry(leafCategoryId, leafCategoryName));
        replicaProductService.applyProductChanged(new ProductChangedEvent(UUID.randomUUID(),
            RecommendFixture.PRODUCT_CHANGED_AT, new ProductChangedEvent.Data(productId, data.sellerId(),
            data.name(), leafCategoryId, data.status(), data.thumbnailUrl(), data.createdAt(), data.sizeFree(),
            data.options(), data.infoValues(), data.brand(), data.description(), data.sizeSpecs(), categoryPath)));
    }

    private RestTestClient.ResponseSpec delete(Long memberId, Long childId, String part) {
        return client.delete().uri(path(childId) + "/" + part)
            .headers(TestMembers.buyer(memberId))
            .exchange();
    }

    private record ErrorText(String title, String detail) {
    }
}
