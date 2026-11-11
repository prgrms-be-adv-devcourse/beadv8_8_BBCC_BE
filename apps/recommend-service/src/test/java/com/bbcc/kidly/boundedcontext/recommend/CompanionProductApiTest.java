package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.OTHER_MEMBER_ID;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 함께 사면 좋은 상품 API를 HTTP 요청부터 응답까지 확인한다.
 * 테스트 환경에는 OpenAI 키가 없어 AI를 부르지 않으므로 늘 규칙 결과(RULE)다.
 * 자녀 1은 키 115cm에 선호 핏이 여유라, 110 · 120 옵션이 있는 상품은 120을 추천한다.
 */
@FunctionalTest
class CompanionProductApiTest {

    private static final String PATH = "/api/v1/recommendations/companions";

    private static final Long VIEWED_PRODUCT_ID = 7001L;

    private static final Long BOTTOM_PRODUCT_ID = 101L;

    private static final List<ProductChangedEvent.CategoryPathEntry> TOP = List.of(
        new ProductChangedEvent.CategoryPathEntry(1L, "의류"), new ProductChangedEvent.CategoryPathEntry(52L, "상의"));

    private static final List<ProductChangedEvent.CategoryPathEntry> BOTTOM = List.of(
        new ProductChangedEvent.CategoryPathEntry(1L, "의류"), new ProductChangedEvent.CategoryPathEntry(60L, "하의"));

    @Autowired
    private RestTestClient client;

    @Autowired
    private RecommendReplicaProductService replicaProductService;

    @Autowired
    private RecommendReplicaChildService replicaChildService;

    @Autowired
    private RecommendReplicaSellerService replicaSellerService;

    @BeforeEach
    void setUp() {
        replicaSellerService.applySellerChanged(RecommendReplicaSellerFixture.sellerApprovedEvent());
        saveProduct(VIEWED_PRODUCT_ID, TOP);
    }

    @Test
    @DisplayName("PAIR-06 AI 결과가 없으면 200이고 출처는 RULE, 상품 카드는 판매자 이름 · 최저가 · 추천 사이즈를 담고 이유는 null이다")
    void returnRuleCompanionsWithProductCards() {
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());
        saveProduct(BOTTOM_PRODUCT_ID, BOTTOM);

        // ID는 문자열로, 금액은 숫자로 보낸다. 값의 타입까지 확인하려고 Map으로 꺼내 비교한다
        companions(VIEWED_PRODUCT_ID)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.source").isEqualTo("RULE")
            .jsonPath("$.products.length()").isEqualTo(1)
            .jsonPath("$.products[0]").value(Map.class, card -> assertThat(card).isEqualTo(bottomCard()));
    }

    @Test
    @DisplayName("후보가 없으면 200이고 products는 빈 목록이다")
    void returnEmptyProductsWhenNoCandidates() {
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());

        companions(VIEWED_PRODUCT_ID)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.source").isEqualTo("RULE")
            .jsonPath("$.products").isArray()
            .jsonPath("$.products.length()").isEqualTo(0);
    }

    @Test
    @DisplayName("로그인하지 않으면 401을 받는다")
    void rejectWithoutLogin() {
        client.get().uri(PATH + "?productId={productId}&childId={childId}", VIEWED_PRODUCT_ID, CHILD_1_ID)
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @ParameterizedTest
    @ValueSource(strings = {"?childId=1", "?productId=7001"})
    @DisplayName("productId나 childId 없이 요청하면 400을 ProblemDetail로 받는다")
    void rejectWithoutRequiredParameter(String query) {
        expectProblem(client.get().uri(PATH + query)
                .headers(TestMembers.buyer(MEMBER_1_ID))
                .exchange(), HttpStatus.BAD_REQUEST)
            .jsonPath("$.title").exists()
            .jsonPath("$.detail").exists();
    }

    @Test
    @DisplayName("보고 있는 상품이 없으면 404 \"상품을 찾을 수 없습니다.\"를 ProblemDetail로 받는다")
    void rejectUnknownProduct() {
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());

        expectProblem(companions(999L), HttpStatus.NOT_FOUND)
            .jsonPath("$.title").isEqualTo("상품을 찾을 수 없습니다")
            .jsonPath("$.detail").isEqualTo("상품을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("다른 회원의 자녀면 404 \"자녀를 찾을 수 없습니다.\"를 ProblemDetail로 받는다")
    void rejectOtherMembersChild() {
        replicaChildService.applyChildChanged(childOf(OTHER_MEMBER_ID));

        expectProblem(companions(VIEWED_PRODUCT_ID), HttpStatus.NOT_FOUND)
            .jsonPath("$.title").isEqualTo("자녀를 찾을 수 없습니다")
            .jsonPath("$.detail").isEqualTo("자녀를 찾을 수 없습니다.");
    }

    private RestTestClient.ResponseSpec companions(Long productId) {
        return client.get().uri(PATH + "?productId={productId}&childId={childId}", productId, CHILD_1_ID)
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
            .jsonPath("$.instance").isEqualTo(PATH);
    }

    // 이유는 null이라 Map.of로 만들 수 없어 HashMap에 담는다
    private static Map<String, Object> bottomCard() {
        Map<String, Object> card = new HashMap<>();
        card.put("productId", String.valueOf(BOTTOM_PRODUCT_ID));
        card.put("name", "상품 " + BOTTOM_PRODUCT_ID);
        card.put("thumbnailUrl", PRODUCT_A_THUMBNAIL_URL);
        card.put("businessName", BUSINESS_NAME);
        card.put("minPrice", 25_000);
        card.put("recommendedSize", "120");
        card.put("reason", null);
        return card;
    }

    // 판매자 복사본이 있는 상품 A의 판매자로, 자녀 1에게 맞는 110 · 120 옵션(판매가 25,000원)을 가진 상품이다
    private void saveProduct(Long productId, List<ProductChangedEvent.CategoryPathEntry> categoryPath) {
        replicaProductService.applyProductChanged(new ProductChangedEvent(UUID.randomUUID(), PRODUCT_CHANGED_AT,
            new ProductChangedEvent.Data(productId, PRODUCT_A_SELLER_ID, "상품 " + productId,
                categoryPath.getLast().id(), ProductStatus.ON_SALE, PRODUCT_A_THUMBNAIL_URL, PRODUCT_A_REGISTERED_AT,
                false, List.of(
                    RecommendFixture.productOption(1L, "110", 105, 115),
                    RecommendFixture.productOption(2L, "120", 115, 125)),
                List.of(), null, null, List.of(), categoryPath, false, PRODUCT_A_REGISTERED_AT)));
    }

    private static ChildChangedEvent childOf(Long memberId) {
        return new ChildChangedEvent(UUID.randomUUID(), RecommendFixture.CHILD_CHANGED_AT, new ChildChangedEvent.Data(
            CHILD_1_ID, memberId, new BigDecimal("115.0"), new BigDecimal("20.0"), Gender.MALE,
            RecommendFixture.CHILD_1_BIRTH_DATE, BodyType.NORMAL, PreferredFit.LOOSE, false));
    }
}
