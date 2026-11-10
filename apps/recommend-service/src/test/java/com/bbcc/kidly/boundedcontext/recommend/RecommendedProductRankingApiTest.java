package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_CATEGORY_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_NAME;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_REGISTERED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_SELLER_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_THUMBNAIL_URL;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_CHANGED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.EmbeddingVectorFixture.axis;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceSnapshotStore;
import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaChildService;
import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaProductService;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.CategoryAffinity;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityType;
import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.EmbeddingVector;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceSnapshot;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.TasteVector;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.FunctionalTest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 더보기 목록이 1쪽 기준 시각(asOf)과 저장된 선호로 순서를 정하는 동작을 HTTP 요청부터 응답까지 확인한다.
 */
@FunctionalTest
class RecommendedProductRankingApiTest {

    private static final String PATH = "/api/v1/recommendations/products";

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private static final Long OTHER_CATEGORY_ID = 53L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private RecommendReplicaChildService replicaChildService;

    @Autowired
    private RecommendReplicaProductService replicaProductService;

    @Autowired
    private PreferenceSnapshotStore snapshotStore;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());
    }

    @Test
    @DisplayName("PREF-09 asOf를 주면 응답의 asOf로 그 값을 그대로 돌려준다")
    void echoGivenAsOf() {
        list("&asOf=2026-11-09T10:00:00")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.asOf").isEqualTo("2026-11-09T10:00:00");
    }

    @Test
    @DisplayName("PREF-09 asOf 없이 요청하면 지금 시각(한국 시간, 초 단위)을 응답의 asOf로 돌려준다")
    void useNowWhenAsOfIsMissing() {
        LocalDateTime before = LocalDateTime.now(SERVICE_ZONE).truncatedTo(ChronoUnit.SECONDS);

        String asOf = list("")
            .expectStatus().isOk()
            .returnResult(AsOfOnly.class)
            .getResponseBody()
            .asOf();

        LocalDateTime after = LocalDateTime.now(SERVICE_ZONE);
        assertThat(LocalDateTime.parse(asOf)).isBetween(before, after).hasNano(0);
    }

    @Test
    @DisplayName("PREF-06 취향 벡터가 없는 자녀는 저장된 카테고리 친밀도가 있는 상품이 더 늦게 판매를 시작한 상품보다 먼저 나온다")
    void orderByStoredCategoryAffinity() {
        saveProduct(301L, PRODUCT_A_CATEGORY_ID, PRODUCT_A_REGISTERED_AT);
        saveProduct(302L, OTHER_CATEGORY_ID, PRODUCT_A_REGISTERED_AT.plusDays(1));
        snapshotStore.replaceChild(CHILD_1_ID, new PreferenceSnapshot(
            List.of(new CategoryAffinity(PRODUCT_A_CATEGORY_ID, 0.6)), List.of(), null, PRODUCT_CHANGED_AT));

        list("")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[*].productId").isEqualTo(List.of("301", "302"));
    }

    @Test
    @DisplayName("PREF-06 취향 벡터가 있는 자녀는 취향 벡터와 가까운 상품이 더 늦게 판매를 시작한 상품보다 먼저 나온다")
    void orderByTasteSimilarity() {
        saveProduct(401L, PRODUCT_A_CATEGORY_ID, PRODUCT_A_REGISTERED_AT);
        saveProduct(402L, PRODUCT_A_CATEGORY_ID, PRODUCT_A_REGISTERED_AT.plusDays(1));
        // 401은 취향 벡터와 같은 방향이라 유사도 1이고, 402는 수직이라 유사도 0이다
        saveEmbedding(401L, axis(0));
        saveEmbedding(402L, axis(1));
        snapshotStore.replaceChild(CHILD_1_ID, new PreferenceSnapshot(
            List.of(), List.of(), new TasteVector(axis(0), 1.0, 1), PRODUCT_CHANGED_AT));

        list("")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[*].productId").isEqualTo(List.of("401", "402"));
    }

    @Test
    @DisplayName("PREF-08 회원의 장바구니에 있는 상품과 그 자녀가 구매확정한 상품은 목록에서 빠진다")
    void excludeCartAndPurchasedProducts() {
        saveProduct(501L, PRODUCT_A_CATEGORY_ID, PRODUCT_A_REGISTERED_AT);
        saveProduct(502L, PRODUCT_A_CATEGORY_ID, PRODUCT_A_REGISTERED_AT);
        saveProduct(503L, PRODUCT_A_CATEGORY_ID, PRODUCT_A_REGISTERED_AT);
        LocalDateTime yesterday = LocalDateTime.now(SERVICE_ZONE).minusDays(1);
        PreferenceTestData.insertActivity(jdbc, ActivityType.CART_ITEM_ADDED, MEMBER_1_ID, null, yesterday,
            502L, null, null, 1L);
        PreferenceTestData.insertPurchase(jdbc, CHILD_1_ID, 503L, yesterday);

        list("")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[*].productId").isEqualTo(List.of("501"));
    }

    @Test
    @DisplayName("PREF-12 자녀를 고르지 않은 회원 활동으로 계산한 친밀도도 그 회원의 자녀 순서에 반영된다")
    void orderByMemberAffinity() {
        saveProduct(601L, PRODUCT_A_CATEGORY_ID, PRODUCT_A_REGISTERED_AT);
        saveProduct(602L, OTHER_CATEGORY_ID, PRODUCT_A_REGISTERED_AT.plusDays(1));
        snapshotStore.replaceMember(MEMBER_1_ID, new PreferenceSnapshot(
            List.of(new CategoryAffinity(PRODUCT_A_CATEGORY_ID, 0.6)), List.of(), null, PRODUCT_CHANGED_AT));

        list("")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[*].productId").isEqualTo(List.of("601", "602"));
    }

    @Test
    @DisplayName("PREF-09 1쪽을 본 뒤 기준 시각보다 늦게 판매를 시작한 상품이 생겨도 같은 asOf로 받은 2쪽에 중복 · 누락이 없다")
    void samePagesWithSameAsOf() {
        LocalDateTime asOf = LocalDateTime.of(2026, 11, 9, 10, 0);
        saveProduct(701L, PRODUCT_A_CATEGORY_ID, PRODUCT_A_REGISTERED_AT);
        saveProduct(702L, PRODUCT_A_CATEGORY_ID, PRODUCT_A_REGISTERED_AT.plusDays(1));
        list("&page=0&size=1&asOf=" + asOf)
            .expectBody().jsonPath("$.content[*].productId").isEqualTo(List.of("702"));

        saveProduct(703L, PRODUCT_A_CATEGORY_ID, asOf.plusHours(1));

        list("&page=1&size=1&asOf=" + asOf)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[*].productId").isEqualTo(List.of("701"));
    }

    @Test
    @DisplayName("PREF-08 1쪽을 본 뒤 그 상품을 장바구니에 담아도 같은 asOf로 받은 2쪽에 중복 · 누락이 없다")
    void samePagesAfterCartChange() {
        LocalDateTime asOf = LocalDateTime.now(SERVICE_ZONE).minusMinutes(1).truncatedTo(ChronoUnit.SECONDS);
        saveProduct(801L, PRODUCT_A_CATEGORY_ID, PRODUCT_A_REGISTERED_AT);
        saveProduct(802L, PRODUCT_A_CATEGORY_ID, PRODUCT_A_REGISTERED_AT.plusDays(1));
        list("&page=0&size=1&asOf=" + asOf)
            .expectBody().jsonPath("$.content[*].productId").isEqualTo(List.of("802"));

        PreferenceTestData.insertActivity(jdbc, ActivityType.CART_ITEM_ADDED, MEMBER_1_ID, null,
            LocalDateTime.now(SERVICE_ZONE), 802L, null, null, 9L);

        list("&page=1&size=1&asOf=" + asOf)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[*].productId").isEqualTo(List.of("801"));
    }

    @Test
    @DisplayName("asOf가 지금보다 늦으면 400 \"잘못된 기준 시각입니다\"를 ProblemDetail로 받는다")
    void rejectFutureAsOf() {
        LocalDateTime tomorrow = LocalDateTime.now(SERVICE_ZONE).plusDays(1).truncatedTo(ChronoUnit.SECONDS);

        expectProblem(list("&asOf=" + tomorrow), HttpStatus.BAD_REQUEST)
            .jsonPath("$.title").isEqualTo("잘못된 기준 시각입니다")
            .jsonPath("$.detail").isEqualTo("기준 시각은 지금보다 늦을 수 없습니다.");
    }

    @Test
    @DisplayName("asOf가 날짜 · 시각 형식이 아니면 400을 ProblemDetail로 받는다")
    void rejectMalformedAsOf() {
        expectProblem(list("&asOf=yesterday"), HttpStatus.BAD_REQUEST);
    }

    private RestTestClient.ResponseSpec list(String query) {
        return client.get().uri(PATH + "?childId={childId}" + query, CHILD_1_ID)
            .headers(TestMembers.buyer(MEMBER_1_ID))
            .exchange();
    }

    // 실패 응답이 ProblemDetail 5칸을 담는지 확인하고, title · detail은 부르는 쪽이 이어서 확인한다
    private static RestTestClient.BodyContentSpec expectProblem(
        RestTestClient.ResponseSpec response, HttpStatus status) {
        return response
            .expectStatus().isEqualTo(status)
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.type").isEqualTo("about:blank")
            .jsonPath("$.title").isNotEmpty()
            .jsonPath("$.status").isEqualTo(status.value())
            .jsonPath("$.detail").isNotEmpty()
            .jsonPath("$.instance").isEqualTo(PATH);
    }

    // 상품 A와 같은 판매자 · 옵션(110 · 120)으로 상품 ID · 카테고리 · 등록 시각만 바꾼다. 자녀 1(115cm)에게 맞는 사이즈가 있다
    private void saveProduct(Long productId, Long categoryId, LocalDateTime registeredAt) {
        replicaProductService.applyProductChanged(new ProductChangedEvent(UUID.randomUUID(), PRODUCT_CHANGED_AT,
            new ProductChangedEvent.Data(productId, PRODUCT_A_SELLER_ID, PRODUCT_A_NAME, categoryId,
                ProductStatus.ON_SALE, PRODUCT_A_THUMBNAIL_URL, registeredAt, false, List.of(
                    RecommendFixture.productOption(1L, "110", 105, 115),
                    RecommendFixture.productOption(2L, "120", 115, 125)))));
    }

    // pgvector 글 형식([1.0, 0.0, ...])으로 상품 복사본의 벡터를 넣는다
    private void saveEmbedding(Long productId, EmbeddingVector vector) {
        jdbc.update("UPDATE recommend.replica_product SET embedding = CAST(? AS recommend.vector) WHERE product_id = ?",
            Arrays.toString(vector.values()), productId);
    }

    private record AsOfOnly(String asOf) {
    }
}
