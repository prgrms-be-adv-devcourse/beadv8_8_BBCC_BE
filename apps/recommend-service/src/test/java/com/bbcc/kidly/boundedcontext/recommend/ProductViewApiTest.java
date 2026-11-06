package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityType;
import com.bbcc.kidly.boundedcontext.recommend.out.activity.JpaActivityRecordRepository;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.support.FunctionalTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class ProductViewApiTest {

    private static final String PATH = "/api/v1/recommendations/product-views";

    @Autowired
    private RestTestClient client;

    @Autowired
    private JpaActivityRecordRepository activityRecordRepository;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"{\"productId\": 31}", "{\"productId\": \"31\"}"})
    @DisplayName("로그인한 회원이 숫자나 문자열 상품 ID로 상세 진입을 기록하면 204와 빈 본문을 받고, 활동 기록에 상세 진입 한 줄이 남는다")
    void recordProductView(String body) {
        view(MEMBER_1_ID, body)
            .expectStatus().isNoContent()
            .expectBody().isEmpty();

        assertThat(activityRecordRepository.findAll()).singleElement()
            .extracting(ActivityRecord::getType, ActivityRecord::getMemberId, ActivityRecord::getProductId,
                ActivityRecord::getChildProfileId)
            .containsExactly(ActivityType.PRODUCT_VIEWED, MEMBER_1_ID, PRODUCT_A_ID, null);
    }

    @Test
    @DisplayName("같은 상품의 상세에 두 번 들어가면 기록이 두 줄 남는다")
    void recordSameProductTwice() {
        view(MEMBER_1_ID, "{\"productId\": 31}").expectStatus().isNoContent();
        view(MEMBER_1_ID, "{\"productId\": 31}").expectStatus().isNoContent();

        assertThat(activityRecordRepository.findAll())
            .extracting(ActivityRecord::getType, ActivityRecord::getProductId)
            .containsExactly(tuple(ActivityType.PRODUCT_VIEWED, PRODUCT_A_ID),
                tuple(ActivityType.PRODUCT_VIEWED, PRODUCT_A_ID));
    }

    @Test
    @DisplayName("로그인하지 않으면 401을 받고 기록은 남지 않는다")
    void rejectWithoutToken() {
        client.post().uri(PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"productId\": 31}")
            .exchange()
            .expectStatus().isUnauthorized();

        assertThat(activityRecordRepository.findAll()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "{}                    | productId: 상품 ID가 필요합니다.",
        "{\"productId\": null} | productId: 상품 ID가 필요합니다.",
        "{\"productId\": 0}    | productId: 상품 ID는 1 이상이어야 합니다.",
        "{\"productId\": -1}   | productId: 상품 ID는 1 이상이어야 합니다."})
    @DisplayName("상품 ID가 없거나 1보다 작으면 400 ProblemDetail을 받고 기록은 남지 않는다")
    void rejectInvalidProductId(String body, String detail) {
        view(MEMBER_1_ID, body)
            .expectStatus().isBadRequest()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.type").isEqualTo("about:blank")
            .jsonPath("$.title").isEqualTo("잘못된 요청입니다")
            .jsonPath("$.status").isEqualTo(400)
            .jsonPath("$.detail").isEqualTo(detail)
            .jsonPath("$.instance").isEqualTo(PATH);

        assertThat(activityRecordRepository.findAll()).isEmpty();
    }

    private RestTestClient.ResponseSpec view(Long memberId, String body) {
        return client.post().uri(PATH)
            .headers(TestMembers.buyer(memberId))
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange();
    }
}
