package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaChildService;
import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaProductService;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    @Test
    @DisplayName("안내가 붙으면 code와 화면에 보여 줄 message를 함께 받는다 (키 115cm · 몸무게 18kg · 체형 보통 · 선호 핏 여유)")
    void recommendSizeWithNotices() {
        replicaProductService.applyProductChanged(RecommendFixture.productAChangedEvent());
        replicaChildService.applyChildChanged(childChanged(MEMBER_1_ID, "115.0", "18.0", false));

        recommend(MEMBER_1_ID, PRODUCT_A_ID, CHILD_1_ID)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.recommendedSize").isEqualTo("110")
            .jsonPath("$.fit").isEqualTo("PROPER")
            .jsonPath("$.notices.length()").isEqualTo(2)
            .jsonPath("$.notices[0].code").isEqualTo("BODY_TYPE_ADJUSTED")
            .jsonPath("$.notices[0].message").isEqualTo("입력하신 체형보다 키와 몸무게를 우선해 추천했어요.")
            .jsonPath("$.notices[1].code").isEqualTo("PREFERRED_FIT_NOT_APPLIED")
            .jsonPath("$.notices[1].message").isEqualTo("아이 체형을 먼저 고려해 선호 핏과 다른 사이즈를 추천했어요.")
            .jsonPath("$.candidateSizes").isEqualTo(List.of("110", "120"));
    }

    // 자녀 1의 자녀 정보 변경됨에서 회원 · 키 · 몸무게 · 삭제 여부만 바꾼다. 체형 보통 · 선호 핏 여유는 그대로다
    private static ChildChangedEvent childChanged(Long memberId, String heightCm, String weightKg, boolean deleted) {
        return new ChildChangedEvent(UUID.randomUUID(), RecommendFixture.CHILD_CHANGED_AT, new ChildChangedEvent.Data(
            CHILD_1_ID, memberId, new BigDecimal(heightCm), new BigDecimal(weightKg), Gender.MALE,
            RecommendFixture.CHILD_1_BIRTH_DATE, BodyType.NORMAL, PreferredFit.LOOSE, deleted));
    }

    private RestTestClient.ResponseSpec recommend(Long memberId, Long productId, Long childId) {
        return client.get().uri(PATH + "?childId={childId}", productId, childId)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(memberId)))
            .exchange();
    }
}
