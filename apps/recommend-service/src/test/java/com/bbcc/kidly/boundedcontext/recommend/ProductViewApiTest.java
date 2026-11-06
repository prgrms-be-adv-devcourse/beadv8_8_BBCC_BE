package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityType;
import com.bbcc.kidly.boundedcontext.recommend.out.activity.JpaActivityRecordRepository;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.support.FunctionalTest;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 상품 상세 진입 기록 API를 HTTP 요청부터 응답까지 확인한다.
 */
@FunctionalTest
class ProductViewApiTest {

    private static final String PATH = "/api/v1/recommendations/product-views";

    @Autowired
    private RestTestClient client;

    // 저장된 기록 전체를 읽으려고 JpaRepository의 기본 조회를 쓴다
    @Autowired
    private JpaActivityRecordRepository activityRecordRepository;

    @Test
    @DisplayName("로그인한 회원이 상세 진입을 기록하면 204와 빈 본문을 받고, 활동 기록에 상세 진입 한 줄이 남는다")
    void recordProductView() {
        view(MEMBER_1_ID, Map.of("productId", PRODUCT_A_ID))
            .expectStatus().isNoContent()
            .expectBody().isEmpty();

        assertThat(activityRecordRepository.findAll()).singleElement()
            .extracting(ActivityRecord::getType, ActivityRecord::getMemberId, ActivityRecord::getProductId,
                ActivityRecord::getChildProfileId)
            .containsExactly(ActivityType.PRODUCT_VIEWED, MEMBER_1_ID, PRODUCT_A_ID, null);
    }

    private RestTestClient.ResponseSpec view(Long memberId, Object body) {
        return client.post().uri(PATH)
            .headers(TestMembers.buyer(memberId))
            .body(body)
            .exchange();
    }
}
