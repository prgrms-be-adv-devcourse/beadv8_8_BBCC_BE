package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;

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

    private static final Long BOTTOM_CATEGORY_ID = 70L;

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

    private static Map<String, Object> garment(String part, String categoryId, Map<String, Double> measurements) {
        return Map.of("part", part, "categoryId", categoryId, "measurements", measurements);
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
}
