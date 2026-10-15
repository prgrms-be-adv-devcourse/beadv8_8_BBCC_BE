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
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

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
            .headers(headers -> headers.setBearerAuth(tokens.buyer(MEMBER_1_ID)))
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
        return new ProductChangedEvent(UUID.randomUUID(), PRODUCT_CHANGED_AT,
            new ProductChangedEvent.Data(productId, PRODUCT_A_SELLER_ID, PRODUCT_A_NAME, PRODUCT_A_CATEGORY_ID,
                ProductStatus.ON_SALE, PRODUCT_A_THUMBNAIL_URL, registeredAt, false, List.of(
                    RecommendFixture.productOption(1L, "110", 105, 115),
                    RecommendFixture.productOption(2L, "120", 115, 125))));
    }
}
