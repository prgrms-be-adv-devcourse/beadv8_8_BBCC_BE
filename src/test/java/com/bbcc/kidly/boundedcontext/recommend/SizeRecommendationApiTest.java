package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaChildService;
import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaProductService;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 사이즈 추천 API를 HTTP 요청부터 응답까지 확인한다.
 * 추천은 이벤트로 받아 둔 복사본으로만 계산하므로, 복사본은 리스너가 부르는 서비스에 이벤트를 넘겨 만든다.
 * 자녀 1은 2019-01-01생 남아(키 115cm, 몸무게 20kg, 체형 보통, 선호 핏 여유)이고, 상품 A는 110(105~115cm) · 120(115~125cm)이다.
 */
@FunctionalTest
class SizeRecommendationApiTest {

    private static final String PATH = "/api/v1/products/{productId}/size-recommendation";

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Autowired
    private RecommendReplicaProductService replicaProductService;

    @Autowired
    private RecommendReplicaChildService replicaChildService;

    @Test
    @DisplayName("자녀 1이 상품 A의 사이즈 추천을 받으면 120 · 여유 있음과 후보 110 · 120을 받고, 안내는 빈 목록이고 지난 구매 사이즈는 null이다")
    void recommendSize() {
        replicaProductService.applyProductChanged(RecommendFixture.productAChangedEvent());
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());

        recommend(MEMBER_1_ID, PRODUCT_A_ID, CHILD_1_ID)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.productId").isEqualTo(String.valueOf(PRODUCT_A_ID))
            .jsonPath("$.childId").isEqualTo(String.valueOf(CHILD_1_ID))
            .jsonPath("$.recommendedSize").isEqualTo("120")
            .jsonPath("$.fit").isEqualTo("ROOMY")
            .jsonPath("$.notices.length()").isEqualTo(0)
            .jsonPath("$.candidateSizes").isEqualTo(List.of("110", "120"))
            // 없는 값은 칸을 빼지 않고 null로 보낸다
            .jsonPath("$").value(Map.class, body -> assertThat(body).containsEntry("lastPurchasedSize", null));
    }

    private RestTestClient.ResponseSpec recommend(Long memberId, Long productId, Long childId) {
        return client.get().uri(PATH + "?childId={childId}", productId, childId)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(memberId)))
            .exchange();
    }
}
