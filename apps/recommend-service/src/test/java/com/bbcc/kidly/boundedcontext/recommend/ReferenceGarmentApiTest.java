package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaChildService;
import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaProductService;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.FunctionalTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 자녀별 기준 옷 API를 HTTP 요청부터 응답까지 확인한다.
 * 자녀는 이벤트로 받아 둔 복사본으로만 알 수 있으므로, 복사본은 리스너가 부르는 서비스에 이벤트를 넘겨 만든다.
 * 자녀 1은 회원 1의 자녀다. 세부 종류(상품 말단 카테고리)는 상품 복사본의 카테고리 경로에서 찾으므로,
 * 상의(ID 7)와 하의(ID 70) 카테고리를 쓰는 상품 복사본을 미리 만들어 둔다.
 */
@FunctionalTest
class ReferenceGarmentApiTest {

    private static final Long CLOTHING_ID = 30L;

    private static final Long TOP_CATEGORY_ID = 7L;

    private static final Long OUTER_CATEGORY_ID = 8L;

    private static final Long DRESS_CATEGORY_ID = 9L;

    private static final Long BOTTOM_CATEGORY_ID = 70L;

    private static final Long SKIRT_CATEGORY_ID = 71L;

    private static final String TOP_BODY =
        "{\"categoryId\": 7, \"measurements\": {\"TOTAL_LENGTH\": 42, \"CHEST_WIDTH\": 34.5}}";

    private static final String BOTTOM_BODY =
        "{\"categoryId\": 70, \"measurements\": {\"TOTAL_LENGTH\": 55.5, \"WAIST_WIDTH\": 26}}";

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

        // ID는 문자열로, 실측은 소수 첫째 자리까지(42가 아니라 42.0) 보낸다.
        // 본문을 Map으로 꺼내면 값의 타입(문자열, 실수)까지 비교된다
        put(MEMBER_1_ID, CHILD_1_ID, "TOP", TOP_BODY)
            .expectStatus().isOk()
            .expectBody(Map.class).isEqualTo(top);
        list(MEMBER_1_ID, CHILD_1_ID)
            .expectStatus().isOk()
            .expectBody(List.class).isEqualTo(List.of(top));
    }

    @Test
    @DisplayName("하의를 먼저 저장하고 상의를 나중에 저장해도 목록은 상의, 하의 순서로 나온다")
    void listTopBeforeBottom() {
        put(MEMBER_1_ID, CHILD_1_ID, "BOTTOM", BOTTOM_BODY).expectStatus().isOk();
        put(MEMBER_1_ID, CHILD_1_ID, "TOP", TOP_BODY).expectStatus().isOk();

        list(MEMBER_1_ID, CHILD_1_ID)
            .expectStatus().isOk()
            .expectBody(List.class).isEqualTo(List.of(
                garment("TOP", "7", Map.of("TOTAL_LENGTH", 42.0, "CHEST_WIDTH", 34.5)),
                garment("BOTTOM", "70", Map.of("TOTAL_LENGTH", 55.5, "WAIST_WIDTH", 26.0))));
    }

    @Test
    @DisplayName("같은 구분을 다시 저장하면 새 값이 기존 기준 옷을 교체하고, 빼고 보낸 선택 실측은 사라진다")
    void replaceSamePart() {
        put(MEMBER_1_ID, CHILD_1_ID, "TOP", "{\"categoryId\": 7, \"measurements\": {\"TOTAL_LENGTH\": 42,"
            + " \"CHEST_WIDTH\": 34.5, \"SHOULDER_WIDTH\": 30, \"SLEEVE_LENGTH\": 40.5}}")
            .expectStatus().isOk();

        put(MEMBER_1_ID, CHILD_1_ID, "TOP",
            "{\"categoryId\": 8, \"measurements\": {\"TOTAL_LENGTH\": 45.5, \"CHEST_WIDTH\": 36}}")
            .expectStatus().isOk()
            .expectBody(Map.class).isEqualTo(garment("TOP", "8", Map.of("TOTAL_LENGTH", 45.5, "CHEST_WIDTH", 36.0)));

        list(MEMBER_1_ID, CHILD_1_ID)
            .expectStatus().isOk()
            .expectBody(List.class).isEqualTo(List.of(
                garment("TOP", "8", Map.of("TOTAL_LENGTH", 45.5, "CHEST_WIDTH", 36.0))));
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
    @CsvSource({"TOP, 7", "TOP, 8", "TOP, 9", "BOTTOM, 70", "BOTTOM, 71"})
    @DisplayName("FIT-01 상의 기준 옷은 상의 · 아우터 · 원피스 카테고리로, 하의 기준 옷은 하의 · 치마 카테고리로 저장된다")
    void saveWithMatchingCategory(String part, String categoryId) {
        put(MEMBER_1_ID, CHILD_1_ID, part, body(categoryId, requiredMeasurements(part)))
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$").value(Map.class, body -> assertThat(body)
                .containsEntry("part", part)
                .containsEntry("categoryId", categoryId));
    }

    @ParameterizedTest(name = "{0} 기준 옷에 카테고리 {1}")
    @CsvSource({"TOP, 70", "TOP, 71", "BOTTOM, 7", "BOTTOM, 8", "BOTTOM, 9"})
    @DisplayName("FIT-01 구분과 맞지 않는 카테고리면 400이고 저장되지 않는다")
    void rejectCategoryOfOtherPart(String part, String categoryId) {
        expectProblem(put(MEMBER_1_ID, CHILD_1_ID, part, body(categoryId, requiredMeasurements(part))),
            400, "구분과 맞지 않는 세부 종류입니다",
            "상의 기준 옷은 상의 · 아우터 · 원피스, 하의 기준 옷은 하의 · 치마 카테고리에서 골라 주세요.",
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

    private static Map<String, Object> garment(String part, String categoryId, Map<String, Double> measurements) {
        return Map.of("part", part, "categoryId", categoryId, "measurements", measurements);
    }

    private static String requiredMeasurements(String part) {
        if ("TOP".equals(part)) {
            return "\"TOTAL_LENGTH\": 42, \"CHEST_WIDTH\": 34.5";
        }
        return "\"TOTAL_LENGTH\": 55.5, \"WAIST_WIDTH\": 26";
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
}
