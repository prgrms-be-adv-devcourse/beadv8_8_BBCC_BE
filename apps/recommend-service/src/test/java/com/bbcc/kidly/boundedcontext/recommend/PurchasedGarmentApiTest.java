package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;

import com.bbcc.kidly.boundedcontext.recommend.app.purchase.PurchaseRecordService;
import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaChildService;
import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaProductService;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.FunctionalTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class PurchasedGarmentApiTest {

    private static final Long TOP_PRODUCT_ID = 31L;

    private static final Long TOP_CATEGORY_ID = 7L;

    private static final Long BOTTOM_PRODUCT_ID = 32L;

    private static final Long BOTTOM_CATEGORY_ID = 70L;

    private static final LocalDateTime FIRST_CONFIRMED_AT = LocalDateTime.of(2026, 10, 15, 12, 0);

    private static final LocalDateTime SECOND_CONFIRMED_AT = LocalDateTime.of(2026, 10, 16, 12, 0);

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
        purchase(201L, TOP_PRODUCT_ID, "110", FIRST_CONFIRMED_AT);
        purchase(202L, BOTTOM_PRODUCT_ID, "120", SECOND_CONFIRMED_AT);

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

    private static Map<String, Object> garment(String orderItemId, String productId, String productName,
        String categoryId, String size, Map<String, Double> measurements, String confirmedAt) {
        return Map.of("orderItemId", orderItemId, "productId", productId, "productName", productName,
            "categoryId", categoryId, "size", size, "measurements", measurements, "confirmedAt", confirmedAt);
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
        Map<String, BigDecimal> values = new java.util.HashMap<>();
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

    private void purchase(Long orderItemId, Long productId, String size, LocalDateTime confirmedAt) {
        purchaseRecordService.recordPurchaseConfirmed(new OrderItemPurchaseConfirmedEvent(UUID.randomUUID(),
            confirmedAt, new OrderItemPurchaseConfirmedEvent.Data(orderItemId, 100L, MEMBER_1_ID,
            RecommendFixture.SELLER_ID, CHILD_1_ID, productId, 300L, size, 1, 25_000L, confirmedAt)));
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
