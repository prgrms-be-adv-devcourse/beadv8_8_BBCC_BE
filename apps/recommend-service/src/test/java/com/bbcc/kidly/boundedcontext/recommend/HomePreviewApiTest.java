package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_CATEGORY_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_NAME;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_REGISTERED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_SELLER_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_THUMBNAIL_URL;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_CHANGED_AT;

import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaChildService;
import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaProductService;
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
import org.springframework.beans.factory.annotation.Autowired;
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
