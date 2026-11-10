package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.OTHER_MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaChildService;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityType;
import com.bbcc.kidly.boundedcontext.recommend.out.activity.JpaActivityRecordRepository;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import com.bbcc.kidly.support.FunctionalTest;
import java.math.BigDecimal;
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

    // 추천은 이벤트로 받아 둔 자녀 복사본으로 자녀를 확인하므로, 복사본은 리스너가 부르는 서비스에 이벤트를 넘겨 만든다
    @Autowired
    private RecommendReplicaChildService replicaChildService;

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

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"{\"productId\": 31, \"childId\": 11}", "{\"productId\": 31, \"childId\": \"11\"}"})
    @DisplayName("로그인한 회원이 자기 자녀의 숫자나 문자열 ID를 함께 보내 상세 진입을 기록하면 204와 빈 본문을 받고, 그 자녀의 상세 진입 한 줄이 남는다")
    void recordProductViewOfChild(String body) {
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());

        view(MEMBER_1_ID, body)
            .expectStatus().isNoContent()
            .expectBody().isEmpty();

        assertThat(activityRecordRepository.findAll()).singleElement()
            .extracting(ActivityRecord::getType, ActivityRecord::getMemberId, ActivityRecord::getProductId,
                ActivityRecord::getChildProfileId)
            .containsExactly(ActivityType.PRODUCT_VIEWED, MEMBER_1_ID, PRODUCT_A_ID, CHILD_1_ID);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invisibleChildren")
    @DisplayName("없는 자녀, 다른 회원의 자녀, 삭제된 자녀의 ID로 기록하면 404 ProblemDetail을 받고 기록은 남지 않는다")
    void rejectInvisibleChild(Optional<ChildChangedEvent> childEvent) {
        childEvent.ifPresent(replicaChildService::applyChildChanged);

        view(MEMBER_1_ID, "{\"productId\": 31, \"childId\": 11}")
            .expectStatus().isNotFound()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.type").isEqualTo("about:blank")
            .jsonPath("$.title").isEqualTo("자녀를 찾을 수 없습니다")
            .jsonPath("$.status").isEqualTo(404)
            .jsonPath("$.detail").isEqualTo("자녀를 찾을 수 없습니다.")
            .jsonPath("$.instance").isEqualTo(PATH);

        assertThat(activityRecordRepository.findAll()).isEmpty();
    }

    static Stream<Arguments> invisibleChildren() {
        return Stream.of(
            Arguments.of(Named.of("없는 자녀", Optional.empty())),
            Arguments.of(Named.of("다른 회원의 자녀", Optional.of(childChanged(OTHER_MEMBER_ID, false)))),
            Arguments.of(Named.of("삭제된 자녀", Optional.of(childChanged(MEMBER_1_ID, true)))));
    }

    @Test
    @DisplayName("PREF-15 같은 회원이 같은 상품의 상세에 같은 날 두 번 들어가면 둘 다 204를 받고 기록은 한 줄만 남는다")
    void recordSameProductOnceOnSameDay() {
        view(MEMBER_1_ID, "{\"productId\": 31}").expectStatus().isNoContent();
        view(MEMBER_1_ID, "{\"productId\": 31}").expectStatus().isNoContent().expectBody().isEmpty();

        assertThat(activityRecordRepository.findAll())
            .extracting(ActivityRecord::getType, ActivityRecord::getProductId)
            .containsExactly(tuple(ActivityType.PRODUCT_VIEWED, PRODUCT_A_ID));
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

    // 자녀 1의 자녀 정보 변경됨에서 회원과 삭제 여부만 바꾼다
    private static ChildChangedEvent childChanged(Long memberId, boolean deleted) {
        return new ChildChangedEvent(UUID.randomUUID(), RecommendFixture.CHILD_CHANGED_AT, new ChildChangedEvent.Data(
            CHILD_1_ID, memberId, new BigDecimal("115.0"), new BigDecimal("20.0"), Gender.MALE,
            RecommendFixture.CHILD_1_BIRTH_DATE, BodyType.NORMAL, PreferredFit.LOOSE, deleted));
    }

    private RestTestClient.ResponseSpec view(Long memberId, String body) {
        return client.post().uri(PATH)
            .headers(TestMembers.buyer(memberId))
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange();
    }
}
