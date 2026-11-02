package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.OTHER_MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaChildService;
import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaProductService;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.FunctionalTest;
import java.math.BigDecimal;
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
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 사이즈 추천 API를 HTTP 요청부터 응답까지 확인한다.
 * 추천은 이벤트로 받아 둔 복사본으로만 계산하므로, 복사본은 리스너가 부르는 서비스에 이벤트를 넘겨 만든다.
 * 자녀 1은 2019-01-01생 남아(키 115cm, 몸무게 20kg, 체형 보통, 선호 핏 여유)이고, 상품 A는 110(105~115cm) · 120(115~125cm)이다.
 */
@FunctionalTest
class SizeRecommendationApiTest {

    private static final String PATH = "/api/v1/recommendations/size";

    @Autowired
    private RestTestClient client;

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
            .jsonPath("$.recommendedSize").isEqualTo("120")
            .jsonPath("$.fit").isEqualTo("ROOMY")
            .jsonPath("$.notices.length()").isEqualTo(0)
            .jsonPath("$.candidateSizes").isEqualTo(List.of("110", "120"))
            // ID는 숫자가 아니라 문자열로 보내고, 없는 값은 칸을 빼지 않고 null로 보낸다.
            // jsonPath의 isEqualTo는 숫자도 문자열로 바꿔 비교하므로 Map으로 꺼내 값의 타입까지 확인한다
            .jsonPath("$").value(Map.class, body -> assertThat(body)
                .containsEntry("productId", String.valueOf(PRODUCT_A_ID))
                .containsEntry("childId", String.valueOf(CHILD_1_ID))
                .containsEntry("lastPurchasedSize", null));
    }

    @Test
    @DisplayName("INV-R05 안내가 붙으면 code와 화면에 보여 줄 message를 함께 받는다 (키 115cm · 몸무게 18kg · 체형 보통 · 선호 핏 여유)")
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

    @Test
    @DisplayName("INV-R04 자녀 키에 맞는 사이즈가 없으면 사이즈 · 적합도는 null, 후보는 빈 목록이고 NO_MATCHING_SIZE 안내 하나를 받는다")
    void recommendNoMatchingSize() {
        replicaProductService.applyProductChanged(RecommendFixture.productAChangedEvent());
        replicaChildService.applyChildChanged(childChanged(MEMBER_1_ID, "128.0", "26.0", false));

        recommend(MEMBER_1_ID, PRODUCT_A_ID, CHILD_1_ID)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.notices.length()").isEqualTo(1)
            .jsonPath("$.notices[0].code").isEqualTo("NO_MATCHING_SIZE")
            .jsonPath("$.notices[0].message").isEqualTo("아이 키에 맞는 사이즈가 없어요.")
            .jsonPath("$.candidateSizes.length()").isEqualTo(0)
            .jsonPath("$").value(Map.class, body -> assertThat(body)
                .containsEntry("recommendedSize", null)
                .containsEntry("fit", null));
    }

    @Test
    @DisplayName("INV-R04 사이즈 무관 상품이면 사이즈 · 적합도는 null, 후보는 빈 목록이고 SIZE_FREE 안내 하나를 받는다")
    void recommendSizeFreeProduct() {
        replicaProductService.applyProductChanged(productChanged(ProductStatus.ON_SALE, true,
            List.of(RecommendFixture.productOption(1L, "FREE", null, null))));
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());

        recommend(MEMBER_1_ID, PRODUCT_A_ID, CHILD_1_ID)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.notices.length()").isEqualTo(1)
            .jsonPath("$.notices[0].code").isEqualTo("SIZE_FREE")
            .jsonPath("$.notices[0].message").isEqualTo("사이즈 구분이 없는 상품이에요.")
            .jsonPath("$.candidateSizes.length()").isEqualTo(0)
            .jsonPath("$").value(Map.class, body -> assertThat(body)
                .containsEntry("recommendedSize", null)
                .containsEntry("fit", null));
    }

    @Test
    @DisplayName("판매중지된 상품도 추천의 상품 복사본이 있으면 사이즈를 추천한다")
    void recommendSizeOfStoppedProduct() {
        replicaProductService.applyProductChanged(productChanged(ProductStatus.STOPPED, false, List.of(
            RecommendFixture.productOption(1L, "110", 105, 115),
            RecommendFixture.productOption(2L, "120", 115, 125))));
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());

        recommend(MEMBER_1_ID, PRODUCT_A_ID, CHILD_1_ID)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.recommendedSize").isEqualTo("120");
    }

    @Test
    @DisplayName("추천의 상품 복사본에 없는 상품이면 404 \"상품을 찾을 수 없습니다.\"를 ProblemDetail로 받는다")
    void rejectUnknownProduct() {
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());

        expectProblem(recommend(MEMBER_1_ID, PRODUCT_A_ID, CHILD_1_ID), HttpStatus.NOT_FOUND,
            "상품을 찾을 수 없습니다", "상품을 찾을 수 없습니다.");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invisibleChildren")
    @DisplayName("INV-R02 없는 자녀, 다른 회원의 자녀, 삭제된 자녀는 모두 같은 404 \"자녀를 찾을 수 없습니다.\"를 받는다")
    void rejectInvisibleChild(Optional<ChildChangedEvent> childEvent) {
        replicaProductService.applyProductChanged(RecommendFixture.productAChangedEvent());
        childEvent.ifPresent(replicaChildService::applyChildChanged);

        expectProblem(recommend(MEMBER_1_ID, PRODUCT_A_ID, CHILD_1_ID), HttpStatus.NOT_FOUND,
            "자녀를 찾을 수 없습니다", "자녀를 찾을 수 없습니다.");
    }

    static Stream<Arguments> invisibleChildren() {
        return Stream.of(
            Arguments.of(Named.of("없는 자녀", Optional.empty())),
            Arguments.of(Named.of("다른 회원의 자녀", Optional.of(childChanged(OTHER_MEMBER_ID, "115.0", "20.0", false)))),
            Arguments.of(Named.of("삭제된 자녀", Optional.of(childChanged(MEMBER_1_ID, "115.0", "20.0", true)))));
    }

    @Test
    @DisplayName("상품과 자녀가 둘 다 없으면 상품을 먼저 확인해 상품 404를 받는다")
    void checkProductBeforeChild() {
        expectProblem(recommend(MEMBER_1_ID, PRODUCT_A_ID, CHILD_1_ID), HttpStatus.NOT_FOUND,
            "상품을 찾을 수 없습니다", "상품을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("INV-R02 다른 회원의 자녀로 요청하면 사이즈 무관 상품이어도 SIZE_FREE가 아니라 자녀 404를 받는다")
    void checkChildBeforeSizeFree() {
        replicaProductService.applyProductChanged(productChanged(ProductStatus.ON_SALE, true,
            List.of(RecommendFixture.productOption(1L, "FREE", null, null))));
        replicaChildService.applyChildChanged(childChanged(OTHER_MEMBER_ID, "115.0", "20.0", false));

        expectProblem(recommend(MEMBER_1_ID, PRODUCT_A_ID, CHILD_1_ID), HttpStatus.NOT_FOUND,
            "자녀를 찾을 수 없습니다", "자녀를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("childId 없이 요청하면 400 \"childId: 자녀 ID가 필요합니다.\"를 받는다")
    void rejectWithoutChildId() {
        expectProblem(client.get().uri(PATH + "?productId={productId}", PRODUCT_A_ID)
                .headers(TestMembers.buyer(MEMBER_1_ID))
                .exchange(),
            HttpStatus.BAD_REQUEST, "잘못된 요청입니다", "childId: 자녀 ID가 필요합니다.");
    }

    @Test
    @DisplayName("productId 없이 요청하면 400 \"productId: 상품 ID가 필요합니다.\"를 받는다")
    void rejectWithoutProductId() {
        expectProblem(client.get().uri(PATH + "?childId={childId}", CHILD_1_ID)
                .headers(TestMembers.buyer(MEMBER_1_ID))
                .exchange(),
            HttpStatus.BAD_REQUEST, "잘못된 요청입니다", "productId: 상품 ID가 필요합니다.");
    }

    @Test
    @DisplayName("childId가 숫자가 아니면 400을 받는다")
    void rejectNonNumericChildId() {
        client.get().uri(PATH + "?productId={productId}&childId=abc", PRODUCT_A_ID)
            .headers(TestMembers.buyer(MEMBER_1_ID))
            .exchange()
            .expectStatus().isBadRequest()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    @DisplayName("로그인하지 않으면 401을 받는다")
    void rejectWithoutToken() {
        client.get().uri(PATH + "?productId={productId}&childId={childId}", PRODUCT_A_ID, CHILD_1_ID)
            .exchange()
            .expectStatus().isUnauthorized();
    }

    // 실패 응답이 ProblemDetail 5칸을 모두 담는지 확인한다
    private static void expectProblem(
        RestTestClient.ResponseSpec response, HttpStatus status, String title, String detail) {
        response
            .expectStatus().isEqualTo(status)
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.type").isEqualTo("about:blank")
            .jsonPath("$.title").isEqualTo(title)
            .jsonPath("$.status").isEqualTo(status.value())
            .jsonPath("$.detail").isEqualTo(detail)
            .jsonPath("$.instance").isEqualTo(PATH);
    }

    // 상품 A의 상품 정보 변경됨에서 판매 상태 · 사이즈 무관 여부 · 옵션만 바꾼다
    private static ProductChangedEvent productChanged(
        ProductStatus status, boolean sizeFree, List<ProductChangedEvent.Option> options) {
        return new ProductChangedEvent(UUID.randomUUID(), RecommendFixture.PRODUCT_CHANGED_AT,
            new ProductChangedEvent.Data(PRODUCT_A_ID, RecommendFixture.PRODUCT_A_SELLER_ID, "데일리 맨투맨",
                RecommendFixture.PRODUCT_A_CATEGORY_ID, status, RecommendFixture.PRODUCT_A_THUMBNAIL_URL,
                RecommendFixture.PRODUCT_A_REGISTERED_AT, sizeFree, options));
    }

    // 자녀 1의 자녀 정보 변경됨에서 회원 · 키 · 몸무게 · 삭제 여부만 바꾼다. 체형 보통 · 선호 핏 여유는 그대로다
    private static ChildChangedEvent childChanged(Long memberId, String heightCm, String weightKg, boolean deleted) {
        return new ChildChangedEvent(UUID.randomUUID(), RecommendFixture.CHILD_CHANGED_AT, new ChildChangedEvent.Data(
            CHILD_1_ID, memberId, new BigDecimal(heightCm), new BigDecimal(weightKg), Gender.MALE,
            RecommendFixture.CHILD_1_BIRTH_DATE, BodyType.NORMAL, PreferredFit.LOOSE, deleted));
    }

    private RestTestClient.ResponseSpec recommend(Long memberId, Long productId, Long childId) {
        return client.get().uri(PATH + "?productId={productId}&childId={childId}", productId, childId)
            .headers(TestMembers.buyer(memberId))
            .exchange();
    }
}
