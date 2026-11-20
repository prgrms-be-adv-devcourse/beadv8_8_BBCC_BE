package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaChildAccountService;
import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaChildService;
import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaProductService;
import com.bbcc.kidly.global.security.TestChildren;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.shared.auth.PrincipalHeaders;
import com.bbcc.kidly.shared.auth.event.ChildAccountChangedEvent;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import com.bbcc.kidly.support.FunctionalTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 자녀 토큰(X-Child-Account-Id · X-Child-Guardian-Id)으로 부른 사이즈 추천 · 맞춤 상품 목록 · 홈 맛보기를 확인한다.
 * 자녀 계정 501은 보호자 회원 1의 자녀 1(11)에 연결돼 있고, 같은 보호자의 다른 자녀 12도 있다.
 */
@FunctionalTest
class ChildRecommendationApiTest {

    private static final String SIZE = "/api/v1/recommendations/size";
    private static final String PRODUCTS = "/api/v1/recommendations/products";
    private static final String PREVIEW = "/api/v1/recommendations/products/preview";
    private static final Long ACCOUNT_ID = 501L;
    private static final Long SIBLING_ID = 12L;
    private static final LocalDateTime ACCOUNT_CREATED_AT = LocalDateTime.of(2026, 11, 20, 10, 0);

    @Autowired
    private RestTestClient client;

    @Autowired
    private RecommendReplicaProductService replicaProductService;

    @Autowired
    private RecommendReplicaChildService replicaChildService;

    @Autowired
    private RecommendReplicaChildAccountService childAccountService;

    @BeforeEach
    void setUp() {
        replicaProductService.applyProductChanged(RecommendFixture.productAChangedEvent());
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());
        replicaChildService.applyChildChanged(sibling());
        childAccountService.applyChildAccountChanged(accountChanged(false, ACCOUNT_CREATED_AT));
    }

    @Test
    @DisplayName("자녀 토큰으로 사이즈 추천을 부르면 childId 없이 그 계정의 자녀로 계산한다")
    void recommendSizeForChild() {
        size(child()).expectStatus().isOk()
            .expectBody()
            .jsonPath("$.childId").isEqualTo(String.valueOf(CHILD_1_ID))
            .jsonPath("$.recommendedSize").isEqualTo("120");
    }

    @Test
    @DisplayName("자녀 토큰으로 형제의 childId를 보내도 무시하고 그 계정의 자녀로 계산한다")
    void ignoreChildIdFromChild() {
        client.get().uri(SIZE + "?productId={productId}&childId={childId}", PRODUCT_A_ID, SIBLING_ID)
            .headers(child())
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.childId").isEqualTo(String.valueOf(CHILD_1_ID));
    }

    @Test
    @DisplayName("자녀 토큰으로 사이즈 추천을 스트림으로 부르면 규칙 결과를 rule 이벤트로 받는다")
    void streamSizeForChild() {
        String body = client.get().uri(SIZE + "?productId={productId}", PRODUCT_A_ID)
            .headers(child())
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
            .expectBody(String.class).returnResult().getResponseBody();

        assertThat(body).contains("event:rule", "\"childId\":\"" + CHILD_1_ID + "\"");
    }

    @Test
    @DisplayName("자녀 토큰으로 맞춤 상품 목록을 부르면 그 계정 자녀에게 맞는 상품을 받는다")
    void listProductsForChild() {
        client.get().uri(PRODUCTS).headers(child()).exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[0].productId").isEqualTo(String.valueOf(PRODUCT_A_ID))
            .jsonPath("$.content[0].recommendedSize").isEqualTo("120")
            .jsonPath("$.asOf").isNotEmpty();
    }

    @Test
    @DisplayName("자녀 토큰으로 홈 맛보기를 부르면 규칙 결과를 받고, 스트림으로 부르면 rule 이벤트로 받는다")
    void previewForChild() {
        client.get().uri(PREVIEW).headers(child()).exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.source").isEqualTo("RULE")
            .jsonPath("$.products[0].productId").isEqualTo(String.valueOf(PRODUCT_A_ID));

        String body = client.get().uri(PREVIEW).headers(child()).accept(MediaType.TEXT_EVENT_STREAM).exchange()
            .expectStatus().isOk()
            .expectBody(String.class).returnResult().getResponseBody();
        assertThat(body).contains("event:rule");
    }

    @ParameterizedTest
    @ValueSource(strings = {SIZE, PRODUCTS, PREVIEW})
    @DisplayName("삭제된 자녀 계정이면 403이다")
    void rejectDeletedAccount(String path) {
        childAccountService.applyChildAccountChanged(accountChanged(true, ACCOUNT_CREATED_AT.plusDays(1)));

        expectForbidden(get(path, child()), path);
    }

    @ParameterizedTest
    @ValueSource(strings = {SIZE, PRODUCTS, PREVIEW})
    @DisplayName("보호자 헤더가 계정의 보호자와 다르면 403이다")
    void rejectOtherGuardian(String path) {
        expectForbidden(get(path, TestChildren.of(ACCOUNT_ID, MEMBER_1_ID + 1)), path);
    }

    @Test
    @DisplayName("받은 적 없는 자녀 계정이면 403이다")
    void rejectUnknownAccount() {
        expectForbidden(get(SIZE, TestChildren.of(ACCOUNT_ID + 1, MEMBER_1_ID)), SIZE);
    }

    @Test
    @DisplayName("자녀 계정 헤더만 있고 보호자 헤더가 없으면 401이다")
    void rejectMissingGuardianHeader() {
        get(SIZE, headers -> headers.set(PrincipalHeaders.CHILD_ACCOUNT_ID, String.valueOf(ACCOUNT_ID)))
            .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("주체 헤더가 없으면 회원 메서드가 받아 401이다")
    void rejectAnonymous() {
        get(PRODUCTS, TestMembers.anonymous()).expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("회원 헤더와 자녀 헤더가 함께 오면 회원 요청으로 처리해 childId로 고른 자녀를 계산한다")
    void memberHeadersWinOverChildHeaders() {
        client.get().uri(SIZE + "?productId={productId}&childId={childId}", PRODUCT_A_ID, SIBLING_ID)
            .headers(TestMembers.buyer(MEMBER_1_ID).andThen(child()))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.childId").isEqualTo(String.valueOf(SIBLING_ID));
    }

    private RestTestClient.ResponseSpec size(Consumer<HttpHeaders> headers) {
        return client.get().uri(SIZE + "?productId={productId}", PRODUCT_A_ID).headers(headers).exchange();
    }

    private RestTestClient.ResponseSpec get(String path, Consumer<HttpHeaders> headers) {
        return client.get().uri(path + "?productId={productId}", PRODUCT_A_ID).headers(headers).exchange();
    }

    private static void expectForbidden(RestTestClient.ResponseSpec response, String path) {
        response
            .expectStatus().isForbidden()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.title").isEqualTo("자녀 계정을 확인할 수 없습니다")
            .jsonPath("$.status").isEqualTo(HttpStatus.FORBIDDEN.value())
            .jsonPath("$.instance").isEqualTo(path);
    }

    private static Consumer<HttpHeaders> child() {
        return TestChildren.of(ACCOUNT_ID, MEMBER_1_ID);
    }

    private static ChildAccountChangedEvent accountChanged(boolean deleted, LocalDateTime at) {
        return new ChildAccountChangedEvent(UUID.randomUUID(), at,
            new ChildAccountChangedEvent.Data(ACCOUNT_ID, CHILD_1_ID, MEMBER_1_ID, deleted));
    }

    private static ChildChangedEvent sibling() {
        return new ChildChangedEvent(UUID.randomUUID(), RecommendFixture.CHILD_CHANGED_AT, new ChildChangedEvent.Data(
            SIBLING_ID, MEMBER_1_ID, new BigDecimal("100.0"), new BigDecimal("15.0"), Gender.FEMALE,
            RecommendFixture.CHILD_1_BIRTH_DATE.plusYears(2), BodyType.NORMAL, PreferredFit.REGULAR, false));
    }
}
