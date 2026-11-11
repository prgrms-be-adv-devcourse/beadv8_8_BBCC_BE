package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.OTHER_MEMBER_ID;

import com.bbcc.kidly.boundedcontext.recommend.app.purchase.PurchaseRecordService;
import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaChildService;
import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaProductService;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.FunctionalTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class PurchasedGarmentApiTest {

    private static final Long TOP_PRODUCT_ID = 31L;

    private static final Long TOP_CATEGORY_ID = 7L;

    private static final Long BOTTOM_PRODUCT_ID = 32L;

    private static final Long BOTTOM_CATEGORY_ID = 70L;

    private static final LocalDateTime FIRST_CONFIRMED_AT = LocalDateTime.of(2026, 10, 15, 12, 0);

    private static final LocalDateTime SECOND_CONFIRMED_AT = LocalDateTime.of(2026, 10, 16, 12, 0);

    private static final Long SIBLING_CHILD_ID = 14L;

    private static final Long DELETED_CHILD_ID = 13L;

    private static final Long UNKNOWN_CHILD_ID = 99L;

    private static final Long UNKNOWN_PRODUCT_ID = 98L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private RecommendReplicaChildService replicaChildService;

    @Autowired
    private RecommendReplicaProductService replicaProductService;

    @Autowired
    private PurchaseRecordService purchaseRecordService;

    @BeforeEach
    void setUpChild() {
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());
    }

    @Test
    @DisplayName("산 옷이 없는 자녀를 조회하면 200과 빈 목록을 받는다")
    void listEmptyWhenNoPurchase() {
        list(MEMBER_1_ID, CHILD_1_ID)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content").isEmpty()
            .jsonPath("$.page").isEqualTo(0)
            .jsonPath("$.size").isEqualTo(20)
            .jsonPath("$.totalElements").isEqualTo(0)
            .jsonPath("$.totalPages").isEqualTo(0)
            .jsonPath("$.hasNext").isEqualTo(false);
    }

    @Test
    @DisplayName("산 옷은 구매확정이 늦은 것부터 나오고, 줄마다 상품명 · 세부 종류 · 산 사이즈의 실측이 있다")
    void listNewestConfirmedFirstWithSizeMeasurements() {
        applyTopAndBottom();
        purchase(201L, CHILD_1_ID, TOP_PRODUCT_ID, "110", FIRST_CONFIRMED_AT);
        purchase(202L, CHILD_1_ID, BOTTOM_PRODUCT_ID, "120", SECOND_CONFIRMED_AT);

        list(MEMBER_1_ID, CHILD_1_ID)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.totalElements").isEqualTo(2)
            .jsonPath("$.content").isEqualTo(List.of(
                garment("202", "32", "기모 바지", "70", "120",
                    Map.of("TOTAL_LENGTH", 60.0, "WAIST_WIDTH", 27.0), "2026-10-16T12:00:00"),
                garment("201", "31", "데일리 맨투맨", "7", "110",
                    Map.of("TOTAL_LENGTH", 42.0, "CHEST_WIDTH", 34.5), "2026-10-15T12:00:00")));
    }

    @Test
    @DisplayName("같은 보호자의 다른 자녀에게 산 옷은 나오지 않는다")
    void listOnlyThatChildsPurchases() {
        applyTopAndBottom();
        applyChild(SIBLING_CHILD_ID, MEMBER_1_ID, false);
        purchase(201L, CHILD_1_ID, TOP_PRODUCT_ID, "110", FIRST_CONFIRMED_AT);
        purchase(202L, SIBLING_CHILD_ID, BOTTOM_PRODUCT_ID, "120", SECOND_CONFIRMED_AT);

        list(MEMBER_1_ID, CHILD_1_ID)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.totalElements").isEqualTo(1)
            .jsonPath("$.content[0].orderItemId").isEqualTo("201");
    }

    @Test
    @DisplayName("상품 복사본이 없는 산 옷도 나오고, 상품명 · 세부 종류 · 실측은 null이다")
    void listPurchaseWithoutProductReplica() {
        purchase(201L, CHILD_1_ID, UNKNOWN_PRODUCT_ID, "110", FIRST_CONFIRMED_AT);

        list(MEMBER_1_ID, CHILD_1_ID)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content").isEqualTo(List.of(
                garment("201", "98", null, null, "110", null, "2026-10-15T12:00:00")));
    }

    @Test
    @DisplayName("산 사이즈의 실측이 상품에 없으면 실측만 null이다")
    void listPurchaseWithoutSizeMeasurements() {
        applyTopAndBottom();
        purchase(201L, CHILD_1_ID, TOP_PRODUCT_ID, "130", FIRST_CONFIRMED_AT);

        list(MEMBER_1_ID, CHILD_1_ID)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content").isEqualTo(List.of(
                garment("201", "31", "데일리 맨투맨", "7", "130", null, "2026-10-15T12:00:00")));
    }

    @Test
    @DisplayName("한 쪽 크기를 1로 조회하면 가장 늦게 확정된 옷 하나와 다음 쪽이 있다는 값을 받는다")
    void listFirstPageOfOne() {
        applyTopAndBottom();
        purchase(201L, CHILD_1_ID, TOP_PRODUCT_ID, "110", FIRST_CONFIRMED_AT);
        purchase(202L, CHILD_1_ID, BOTTOM_PRODUCT_ID, "120", SECOND_CONFIRMED_AT);

        client.get().uri(path(CHILD_1_ID) + "?page=0&size=1")
            .headers(TestMembers.buyer(MEMBER_1_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(1)
            .jsonPath("$.content[0].orderItemId").isEqualTo("202")
            .jsonPath("$.size").isEqualTo(1)
            .jsonPath("$.totalElements").isEqualTo(2)
            .jsonPath("$.totalPages").isEqualTo(2)
            .jsonPath("$.hasNext").isEqualTo(true);
    }

    @Test
    @DisplayName("다른 보호자의 자녀를 조회하면 403이다")
    void rejectOtherMembersChild() {
        expectProblem(list(OTHER_MEMBER_ID, CHILD_1_ID), 403,
            "자녀에 접근할 수 없습니다", "로그인한 보호자의 자녀만 다룰 수 있습니다.", path(CHILD_1_ID));
    }

    @ParameterizedTest
    @ValueSource(longs = {13L, 99L})
    @DisplayName("없는 자녀나 삭제된 자녀를 조회하면 404다")
    void rejectUnknownOrDeletedChild(Long childId) {
        applyChild(DELETED_CHILD_ID, MEMBER_1_ID, true);

        expectProblem(list(MEMBER_1_ID, childId), 404,
            "자녀를 찾을 수 없습니다", "자녀를 찾을 수 없습니다.", path(childId));
    }

    @Test
    @DisplayName("로그인하지 않고 조회하면 401이다")
    void rejectAnonymous() {
        expectProblem(client.get().uri(path(CHILD_1_ID)).exchange(), 401,
            "로그인이 필요합니다", "로그인 후 다시 시도해 주세요.", path(CHILD_1_ID));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/recommendations/children/abc/purchases",
        "/api/v1/recommendations/children/11/purchases?size=0",
        "/api/v1/recommendations/children/11/purchases?size=101",
        "/api/v1/recommendations/children/11/purchases?page=-1"})
    @DisplayName("자녀 ID 형식이 틀리거나 쪽 번호 · 쪽 크기가 범위 밖이면 400이다")
    void rejectInvalidRequest(String uri) {
        client.get().uri(uri)
            .headers(TestMembers.buyer(MEMBER_1_ID))
            .exchange()
            .expectStatus().isBadRequest()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status").isEqualTo(400);
    }

    // Map.of는 null 값을 받지 않으므로 null 칸을 확인할 수 있게 HashMap으로 만든다
    private static Map<String, Object> garment(String orderItemId, String productId, String productName,
        String categoryId, String size, Map<String, Double> measurements, String confirmedAt) {
        Map<String, Object> garment = new HashMap<>();
        garment.put("orderItemId", orderItemId);
        garment.put("productId", productId);
        garment.put("productName", productName);
        garment.put("categoryId", categoryId);
        garment.put("size", size);
        garment.put("measurements", measurements);
        garment.put("confirmedAt", confirmedAt);
        return garment;
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

    private void applyChild(Long childId, Long memberId, boolean deleted) {
        replicaChildService.applyChildChanged(new ChildChangedEvent(UUID.randomUUID(),
            RecommendFixture.CHILD_CHANGED_AT, new ChildChangedEvent.Data(childId, memberId,
            new BigDecimal("115.0"), new BigDecimal("20.0"), Gender.MALE, RecommendFixture.CHILD_1_BIRTH_DATE,
            BodyType.NORMAL, PreferredFit.LOOSE, deleted)));
    }

    // 상의 · 하의 모두 110 · 120 실측을 둔다. 산 사이즈의 실측을 고르는지 확인하려고 사이즈마다 값을 다르게 둔다
    private void applyTopAndBottom() {
        applyProduct(TOP_PRODUCT_ID, "데일리 맨투맨", TOP_CATEGORY_ID, List.of(
            sizeSpec("110", Map.of("TOTAL_LENGTH", "42.0", "CHEST_WIDTH", "34.5")),
            sizeSpec("120", Map.of("TOTAL_LENGTH", "46.0", "CHEST_WIDTH", "37.0"))));
        applyProduct(BOTTOM_PRODUCT_ID, "기모 바지", BOTTOM_CATEGORY_ID, List.of(
            sizeSpec("110", Map.of("TOTAL_LENGTH", "55.0", "WAIST_WIDTH", "25.0")),
            sizeSpec("120", Map.of("TOTAL_LENGTH", "60.0", "WAIST_WIDTH", "27.0"))));
    }

    private static ProductChangedEvent.SizeSpec sizeSpec(String size, Map<String, String> measurements) {
        Map<String, BigDecimal> values = new HashMap<>();
        measurements.forEach((code, cm) -> values.put(code, new BigDecimal(cm)));
        return new ProductChangedEvent.SizeSpec(size, null, null, values);
    }

    private void applyProduct(Long productId, String name, Long categoryId, List<ProductChangedEvent.SizeSpec> specs) {
        ProductChangedEvent.Data data = RecommendFixture.productAChangedEvent().data();
        replicaProductService.applyProductChanged(new ProductChangedEvent(UUID.randomUUID(),
            RecommendFixture.PRODUCT_CHANGED_AT, new ProductChangedEvent.Data(productId, data.sellerId(),
            name, categoryId, data.status(), data.thumbnailUrl(), data.createdAt(), data.sizeFree(),
            data.options(), data.infoValues(), data.brand(), data.description(), specs, data.categoryPath())));
    }

    private void purchase(Long orderItemId, Long childId, Long productId, String size, LocalDateTime confirmedAt) {
        purchaseRecordService.recordPurchaseConfirmed(new OrderItemPurchaseConfirmedEvent(UUID.randomUUID(),
            confirmedAt, new OrderItemPurchaseConfirmedEvent.Data(orderItemId, 100L, MEMBER_1_ID,
            RecommendFixture.SELLER_ID, childId, productId, 300L, size, 1, 25_000L, confirmedAt)));
    }

    private static String path(Long childId) {
        return "/api/v1/recommendations/children/" + childId + "/purchases";
    }

    private RestTestClient.ResponseSpec list(Long memberId, Long childId) {
        return client.get().uri(path(childId))
            .headers(TestMembers.buyer(memberId))
            .exchange();
    }
}
