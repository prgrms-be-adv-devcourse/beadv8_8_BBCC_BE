package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
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
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.FunctionalTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 홈 맛보기 4개를 HTTP 요청부터 응답까지 확인한다. 테스트에는 OpenAI 키가 없어 chat 모델이 없으므로 늘 규칙 결과다.
 */
@FunctionalTest
class HomePreviewApiTest {

    private static final String PATH = "/api/v1/recommendations/products/preview";

    @Autowired
    private RestTestClient client;

    @Autowired
    private RecommendReplicaChildService replicaChildService;

    @Autowired
    private RecommendReplicaProductService replicaProductService;

    @Autowired
    private RecommendReplicaSellerService replicaSellerService;

    @BeforeEach
    void setUp() {
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());
    }

    @Test
    @DisplayName("HOME-02 AI를 쓸 수 없으면 선호 점수 순서의 앞 4개를 규칙 결과로 돌려주고 이유는 null이다")
    void ruleResultWithoutAi() {
        for (int i = 1; i <= 6; i++) {
            saveProduct(400L + i, PRODUCT_A_REGISTERED_AT.plusDays(i));
        }

        preview()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.source").isEqualTo("RULE")
            .jsonPath("$.products[*].productId").isEqualTo(List.of("406", "405", "404", "403"))
            .jsonPath("$.products[0].name").isEqualTo(PRODUCT_A_NAME)
            .jsonPath("$.products[0].thumbnailUrl").isEqualTo(PRODUCT_A_THUMBNAIL_URL)
            .jsonPath("$.products[0].businessName").isEmpty()
            .jsonPath("$.products[0].minPrice").isNumber()
            .jsonPath("$.products[0].recommendedSize").isNotEmpty()
            .jsonPath("$.products[0].reason").isEmpty();
    }

    @Test
    @DisplayName("판매자 복사본이 있으면 상품 카드에 판매자 상호를 채운다")
    void fillBusinessNameFromSellerReplica() {
        replicaSellerService.applySellerChanged(RecommendReplicaSellerFixture.sellerApprovedEvent());
        saveProduct(401L, PRODUCT_A_REGISTERED_AT);

        preview()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.products[0].businessName").isEqualTo(BUSINESS_NAME);
    }

    @Test
    @DisplayName("HOME-02 후보가 없으면 상품 목록은 빈 배열이다")
    void emptyProductsWithoutCandidates() {
        preview()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.source").isEqualTo("RULE")
            .jsonPath("$.products").isArray()
            .jsonPath("$.products").isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"999, 11", "1, 9999"})
    @DisplayName("없는 자녀와 다른 회원의 자녀는 같은 404 \"자녀를 찾을 수 없습니다.\"를 ProblemDetail로 받는다")
    void childNotFound(Long memberId, Long childId) {
        client.get().uri(PATH + "?childId={childId}", childId)
            .headers(TestMembers.buyer(memberId))
            .exchange()
            .expectStatus().isNotFound()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.type").isEqualTo("about:blank")
            .jsonPath("$.title").isEqualTo("자녀를 찾을 수 없습니다")
            .jsonPath("$.status").isEqualTo(404)
            .jsonPath("$.detail").isEqualTo("자녀를 찾을 수 없습니다.")
            .jsonPath("$.instance").isEqualTo(PATH);
    }

    @Test
    @DisplayName("childId가 없으면 400이다")
    void rejectMissingChildId() {
        client.get().uri(PATH)
            .headers(TestMembers.buyer(MEMBER_1_ID))
            .exchange()
            .expectStatus().isBadRequest()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status").isEqualTo(400)
            .jsonPath("$.instance").isEqualTo(PATH);
    }

    @Test
    @DisplayName("로그인 헤더가 없으면 401이다")
    void rejectAnonymous() {
        client.get().uri(PATH + "?childId={childId}", CHILD_1_ID)
            .headers(TestMembers.anonymous())
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status").isEqualTo(401)
            .jsonPath("$.instance").isEqualTo(PATH);
    }

    @Test
    @DisplayName("HOME-02 스트림(text/event-stream)으로 부르면 규칙 결과를 rule 이벤트로 보내고, AI 결과가 없으면 그대로 닫는다")
    void streamRuleEventWithoutAi() {
        saveProduct(401L, PRODUCT_A_REGISTERED_AT);

        String body = client.get().uri(PATH + "?childId={childId}", CHILD_1_ID)
            .headers(TestMembers.buyer(MEMBER_1_ID))
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
            .expectHeader().valueEquals("X-Accel-Buffering", "no")
            .expectBody(String.class).returnResult().getResponseBody();

        assertThat(body).contains("event:rule", "\"source\":\"RULE\"", "\"productId\":\"401\"")
            .doesNotContain("event:ai");
    }

    @Test
    @DisplayName("스트림으로 불러도 다른 회원의 자녀면 스트림을 열기 전에 404 ProblemDetail로 받는다")
    void childNotFoundBeforeStream() {
        client.get().uri(PATH + "?childId={childId}", 9999L)
            .headers(TestMembers.buyer(MEMBER_1_ID))
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isNotFound()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.title").isEqualTo("자녀를 찾을 수 없습니다");
    }

    private RestTestClient.ResponseSpec preview() {
        return client.get().uri(PATH + "?childId={childId}", CHILD_1_ID)
            .headers(TestMembers.buyer(MEMBER_1_ID))
            .exchange();
    }

    private void saveProduct(Long productId, LocalDateTime registeredAt) {
        replicaProductService.applyProductChanged(new ProductChangedEvent(UUID.randomUUID(), PRODUCT_CHANGED_AT,
            new ProductChangedEvent.Data(productId, PRODUCT_A_SELLER_ID, PRODUCT_A_NAME, PRODUCT_A_CATEGORY_ID,
                ProductStatus.ON_SALE, PRODUCT_A_THUMBNAIL_URL, registeredAt, false, List.of(
                    RecommendFixture.productOption(1L, "110", 105, 115),
                    RecommendFixture.productOption(2L, "120", 115, 125)))));
    }
}
