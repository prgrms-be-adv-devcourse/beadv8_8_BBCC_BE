package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.OTHER_MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_CATEGORY_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_NAME;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_REGISTERED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_SELLER_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_THUMBNAIL_URL;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_CHANGED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendReplicaSellerFixture.BUSINESS_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaChildService;
import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaProductService;
import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaSellerService;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.FunctionalTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 자녀 맞춤 상품 목록 API를 HTTP 요청부터 응답까지 확인한다.
 * 추천은 이벤트로 받아 둔 복사본으로만 목록을 만들므로, 복사본은 리스너가 부르는 서비스에 이벤트를 넘겨 만든다.
 * 자녀 1은 2019-01-01생 남아(키 115cm, 몸무게 20kg, 체형 보통, 선호 핏 여유)이고, 상품은 모두 110(105~115cm) · 120(115~125cm)이다.
 */
@FunctionalTest
class RecommendedProductApiTest {

    private static final String PATH = "/api/v1/children/{childId}/recommended-products";

    /**
     * 신상품순이 상품 ID 내림차순과 다르도록, ID가 작은 상품을 나중에 등록한 상품으로 둔다.
     */
    private static final Long NEWER_PRODUCT_ID = 201L;

    private static final Long OLDER_PRODUCT_ID = 202L;

    private static final Long OLDEST_PRODUCT_ID = 203L;

    /**
     * 상품 A와 다른 카테고리다. 카테고리로 거르는지 확인할 때 쓴다.
     */
    private static final Long OTHER_CATEGORY_ID = 53L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private RecommendReplicaProductService replicaProductService;

    @Autowired
    private RecommendReplicaChildService replicaChildService;

    @Autowired
    private RecommendReplicaSellerService replicaSellerService;

    @Test
    @DisplayName("자녀 1이 맞춤 상품 목록을 요청하면 200과 목록 형식으로 맞는 상품을 신상품순으로 카드 값과 함께 받는다")
    void listRecommendedProducts() {
        // 오래된 상품을 먼저 저장해서, 저장한 순서와 신상품순이 다르게 한다
        replicaProductService.applyProductChanged(productChanged(OLDER_PRODUCT_ID, PRODUCT_A_REGISTERED_AT));
        replicaProductService.applyProductChanged(
            productChanged(NEWER_PRODUCT_ID, PRODUCT_A_REGISTERED_AT.plusDays(1)));
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());
        replicaSellerService.applySellerChanged(RecommendReplicaSellerFixture.sellerApprovedEvent());

        // 키 115cm는 110과 120 두 후보에 들고, 선호 핏이 여유라 큰 120을 추천한다
        // 두 상품 모두 옵션 110과 120의 판매가가 25,000원이라 최저가도 25,000원이다
        // ID는 숫자가 아니라 문자열로 보내고, 금액은 숫자로 보낸다. jsonPath의 isEqualTo는 숫자도 문자열로 바꿔 비교하므로 Map으로 꺼내 값의 타입까지 확인한다
        client.get().uri(PATH, CHILD_1_ID)
            .headers(TestMembers.buyer(MEMBER_1_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(2)
            .jsonPath("$.content[0]").value(Map.class, card -> assertThat(card).isEqualTo(card(NEWER_PRODUCT_ID)))
            .jsonPath("$.content[1]").value(Map.class, card -> assertThat(card).isEqualTo(card(OLDER_PRODUCT_ID)))
            .jsonPath("$.page").isEqualTo(0)
            .jsonPath("$.size").isEqualTo(20)
            .jsonPath("$.totalElements").isEqualTo(2)
            .jsonPath("$.totalPages").isEqualTo(1)
            .jsonPath("$.hasNext").isEqualTo(false);
    }

    @Test
    @DisplayName("맞는 상품이 없으면 200이고 content는 빈 목록이며 totalElements는 0, hasNext는 false다")
    void emptyListWhenNoProductFits() {
        replicaProductService.applyProductChanged(productChanged(NEWER_PRODUCT_ID, PRODUCT_A_REGISTERED_AT));
        // 키 160cm는 110(105~115cm)과 120(115~125cm) 모두에서 2cm보다 멀어 맞는 사이즈가 없다
        replicaChildService.applyChildChanged(childChanged(MEMBER_1_ID, "160.0", false));

        list("")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content").isEqualTo(List.of())
            .jsonPath("$.totalElements").isEqualTo(0)
            .jsonPath("$.hasNext").isEqualTo(false);
    }

    @Test
    @DisplayName("로그인하지 않으면 401을 받는다")
    void rejectWithoutToken() {
        client.get().uri(PATH, CHILD_1_ID)
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invisibleChildren")
    @DisplayName("없는 자녀, 다른 회원의 자녀, 삭제된 자녀는 모두 같은 404 \"자녀를 찾을 수 없습니다.\"를 ProblemDetail로 받는다")
    void rejectInvisibleChild(Optional<ChildChangedEvent> childEvent) {
        replicaProductService.applyProductChanged(productChanged(NEWER_PRODUCT_ID, PRODUCT_A_REGISTERED_AT));
        childEvent.ifPresent(replicaChildService::applyChildChanged);

        expectProblem(list(""), HttpStatus.NOT_FOUND)
            .jsonPath("$.title").isEqualTo("자녀를 찾을 수 없습니다")
            .jsonPath("$.detail").isEqualTo("자녀를 찾을 수 없습니다.");
    }

    static Stream<Arguments> invisibleChildren() {
        return Stream.of(
            Arguments.of(Named.of("없는 자녀", Optional.empty())),
            Arguments.of(Named.of("다른 회원의 자녀", Optional.of(childChanged(OTHER_MEMBER_ID, "115.0", false)))),
            Arguments.of(Named.of("삭제된 자녀", Optional.of(childChanged(MEMBER_1_ID, "115.0", true)))));
    }

    @Test
    @DisplayName("사이즈 무관 상품은 목록에 나오고 추천 사이즈는 칸을 빼지 않고 null로 받는다")
    void sizeFreeProductHasNullRecommendedSize() {
        replicaProductService.applyProductChanged(productChangedOf(NEWER_PRODUCT_ID, PRODUCT_A_CATEGORY_ID,
            PRODUCT_A_REGISTERED_AT, true, List.of(RecommendFixture.productOption(1L, "FREE", null, null))));
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());

        list("")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(1)
            .jsonPath("$.content[0]").value(Map.class, card -> assertThat(card)
                .containsEntry("productId", String.valueOf(NEWER_PRODUCT_ID))
                .containsEntry("recommendedSize", null));
    }

    @Test
    @DisplayName("categoryId를 주면 카테고리가 같은 상품만 받는다")
    void filterByCategory() {
        replicaProductService.applyProductChanged(productChanged(NEWER_PRODUCT_ID, PRODUCT_A_REGISTERED_AT));
        // 맞는 사이즈가 있는 판매중 상품이지만 카테고리가 달라 빠진다
        replicaProductService.applyProductChanged(productChangedOf(OLDER_PRODUCT_ID, OTHER_CATEGORY_ID,
            PRODUCT_A_REGISTERED_AT, false, sizedOptions()));
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());

        list("?categoryId=" + PRODUCT_A_CATEGORY_ID)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(1)
            .jsonPath("$.content[0].productId").isEqualTo(String.valueOf(NEWER_PRODUCT_ID))
            .jsonPath("$.totalElements").isEqualTo(1);
    }

    @ParameterizedTest(name = "page {0}, size {1}")
    @CsvSource({"-1, 20", "0, 0", "0, 101"})
    @DisplayName("page가 음수이거나 size가 1 ~ 100 밖이면 400을 ProblemDetail로 받는다")
    void rejectInvalidPageRequest(int page, int size) {
        replicaProductService.applyProductChanged(productChanged(NEWER_PRODUCT_ID, PRODUCT_A_REGISTERED_AT));
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());

        // 검증 오류의 title · detail 문구는 공통 처리기가 정하므로 칸이 있는지만 확인한다
        expectProblem(list("?page=" + page + "&size=" + size), HttpStatus.BAD_REQUEST)
            .jsonPath("$.title").exists()
            .jsonPath("$.detail").exists();
    }

    @Test
    @DisplayName("두 번째 페이지는 남은 상품 하나만 받고 다음 페이지가 없다")
    void secondPage() {
        replicaProductService.applyProductChanged(productChanged(OLDER_PRODUCT_ID, PRODUCT_A_REGISTERED_AT));
        replicaProductService.applyProductChanged(
            productChanged(OLDEST_PRODUCT_ID, PRODUCT_A_REGISTERED_AT.minusDays(1)));
        replicaProductService.applyProductChanged(
            productChanged(NEWER_PRODUCT_ID, PRODUCT_A_REGISTERED_AT.plusDays(1)));
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());

        list("?page=1&size=2")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(1)
            .jsonPath("$.content[0].productId").isEqualTo(String.valueOf(OLDEST_PRODUCT_ID))
            .jsonPath("$.page").isEqualTo(1)
            .jsonPath("$.size").isEqualTo(2)
            .jsonPath("$.totalElements").isEqualTo(3)
            .jsonPath("$.totalPages").isEqualTo(2)
            .jsonPath("$.hasNext").isEqualTo(false);
    }

    private RestTestClient.ResponseSpec list(String query) {
        return client.get().uri(PATH + query, CHILD_1_ID)
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
            .jsonPath("$.status").isEqualTo(status.value())
            .jsonPath("$.instance").isEqualTo("/api/v1/children/" + CHILD_1_ID + "/recommended-products");
    }

    private static Map<String, Object> card(Long productId) {
        return Map.of(
            "productId", String.valueOf(productId),
            "name", PRODUCT_A_NAME,
            "thumbnailUrl", PRODUCT_A_THUMBNAIL_URL,
            "businessName", BUSINESS_NAME,
            "minPrice", 25_000,
            "recommendedSize", "120");
    }

    // 상품 A와 같은 판매자 · 카테고리 · 옵션으로, 상품 ID와 등록 시각만 바꾼 상품 정보 변경됨이다
    private static ProductChangedEvent productChanged(Long productId, LocalDateTime registeredAt) {
        return productChangedOf(productId, PRODUCT_A_CATEGORY_ID, registeredAt, false, sizedOptions());
    }

    // 상품 A와 같은 판매자 · 이름 · 썸네일로, 상품 ID · 카테고리 · 등록 시각 · 사이즈 무관 여부 · 옵션을 바꾼 상품 정보 변경됨이다
    private static ProductChangedEvent productChangedOf(Long productId, Long categoryId, LocalDateTime registeredAt,
        boolean sizeFree, List<ProductChangedEvent.Option> options) {
        return new ProductChangedEvent(UUID.randomUUID(), PRODUCT_CHANGED_AT,
            new ProductChangedEvent.Data(productId, PRODUCT_A_SELLER_ID, PRODUCT_A_NAME, categoryId,
                ProductStatus.ON_SALE, PRODUCT_A_THUMBNAIL_URL, registeredAt, sizeFree, options));
    }

    // 상품 A의 옵션인 110(105~115cm)과 120(115~125cm)이고, 판매가는 모두 25,000원이다
    private static List<ProductChangedEvent.Option> sizedOptions() {
        return List.of(
            RecommendFixture.productOption(1L, "110", 105, 115),
            RecommendFixture.productOption(2L, "120", 115, 125));
    }

    // 자녀 1의 자녀 정보 변경됨에서 회원 · 키 · 삭제 여부만 바꾼다. 몸무게 20kg · 체형 보통 · 선호 핏 여유는 그대로다
    private static ChildChangedEvent childChanged(Long memberId, String heightCm, boolean deleted) {
        return new ChildChangedEvent(UUID.randomUUID(), RecommendFixture.CHILD_CHANGED_AT, new ChildChangedEvent.Data(
            CHILD_1_ID, memberId, new BigDecimal(heightCm), new BigDecimal("20.0"), Gender.MALE,
            RecommendFixture.CHILD_1_BIRTH_DATE, BodyType.NORMAL, PreferredFit.LOOSE, deleted));
    }
}
