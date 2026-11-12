package com.bbcc.kidly.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.gateway.support.GatewayTestSupport;
import com.bbcc.kidly.gateway.support.IntegrationTest;
import com.bbcc.kidly.gateway.support.RecordingBackend;
import com.bbcc.kidly.gateway.support.RecordingBackendConfiguration;
import com.bbcc.kidly.gateway.support.TestTokens;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

/**
 * 내부 API를 막고, 상품 목록은 검색 서비스로(#526), 추천은 추천 서비스로(#704), 정산은 정산 서비스로(#729),
 * 회원 · 인증 · 판매자 · 자녀 프로필은 회원 서비스로(#809), 결제는 결제 서비스로(#525), 상품은 상품 서비스로(#791),
 * 주문 · 장바구니 · 배송 · 조르기는 주문 서비스로, 리뷰 · 찜은 커뮤니티 서비스로, 알림함은 알림 서비스로,
 * 나머지는 404 ProblemDetail로 답하는지, CORS를 Gateway가 처리하는지 본다(#608).
 */
@IntegrationTest
class RoutingIntegrationTest extends GatewayTestSupport {

    private static final String ORIGIN = RecordingBackend.ALLOWED_ORIGIN;
    // nginx dev 블록이 받는 이름. 허용 origin(프론트)에는 없다
    private static final String NGINX_HOST = "dev-api.example";
    private static final String CLIENT_IP = "1.2.3.4";

    @Autowired
    @Qualifier(RecordingBackendConfiguration.MEMBER)
    private RecordingBackend memberBackend;

    @Autowired
    @Qualifier(RecordingBackendConfiguration.SEARCH)
    private RecordingBackend searchBackend;

    @Autowired
    @Qualifier(RecordingBackendConfiguration.RECOMMEND)
    private RecordingBackend recommendBackend;

    @Autowired
    @Qualifier(RecordingBackendConfiguration.SETTLEMENT)
    private RecordingBackend settlementBackend;

    @Autowired
    @Qualifier(RecordingBackendConfiguration.PAYMENT)
    private RecordingBackend paymentBackend;

    @BeforeEach
    void resetServiceBackends() {
        memberBackend.reset();
        searchBackend.reset();
        recommendBackend.reset();
        settlementBackend.reset();
        paymentBackend.reset();
    }

    @Test
    @DisplayName("자녀 자기 프로필 조회는 회원 서비스에 넘긴다")
    void routesChildProfileToMemberService() {
        var now = Instant.now();
        var token = TestTokens.issueChild(7001, 8, now, now.plusSeconds(1800));
        client.get().uri("/api/v1/child/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .exchange().expectStatus().isOk();
        assertThat(memberBackend.lastRequest()).hasValueSatisfying(request ->
            assertThat(request.uri().getPath()).isEqualTo("/api/v1/child/profile"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/child/onboarding", "/api/v1/child/onboarding/notice"})
    @DisplayName("자녀 첫 로그인 안내 경로는 회원 서비스에 넘긴다")
    void routesChildOnboardingToMemberService(String path) {
        var now = Instant.now();
        var token = TestTokens.issueChild(7001, 8, now, now.plusSeconds(1800));
        client.method(path.endsWith("/notice") ? HttpMethod.POST : HttpMethod.GET).uri(path)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange().expectStatus().isOk();
        assertThat(memberBackend.lastRequest()).hasValueSatisfying(request ->
            assertThat(request.uri().getPath()).isEqualTo(path));
    }

    @Test
    @DisplayName("다른 자녀 경로는 404 ProblemDetail이고 넘기지 않는다")
    void rejectsOtherChildPaths() {
        var now = Instant.now();
        var token = TestTokens.issueChild(7001, 8, now, now.plusSeconds(1800));
        for (var path : List.of("/api/v1/child/profiles", "/api/v1/child/orders")) {
            var response = client.get().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();
            expectNotFound(response, path);
        }
    }

    @Test
    @DisplayName("서비스끼리만 부르는 내부 API는 404 ProblemDetail이고 넘기지 않는다")
    void blocksInternalApi() {
        expectNotFound(client.get().uri("/internal/members/1").exchange(), "/internal/members/1");

        assertThat(searchBackend.lastRequest()).isEmpty();
    }

    @Test
    @DisplayName("상품 목록 조회는 같은 경로 · 쿼리로 검색 서비스에 넘긴다")
    void routesProductListToSearchService() {
        client.get().uri("/api/v1/products?keyword=원피스&sort=NEWEST&page=0").exchange()
            .expectStatus().isOk();

        assertThat(searchBackend.lastRequest()).hasValueSatisfying(request -> {
            assertThat(request.method()).isEqualTo("GET");
            assertThat(request.uri().getPath()).isEqualTo("/api/v1/products");
            assertThat(request.uri().getQuery()).isEqualTo("keyword=원피스&sort=NEWEST&page=0");
        });
        assertThat(productBackend.lastRequest()).isEmpty();
    }

    @Test
    @DisplayName("상품 목록 경로라도 GET이 아니면 상품 서비스에 넘긴다")
    void routesNonGetProductsToProductService() {
        client.post().uri("/api/v1/products").exchange()
            .expectStatus().isOk();

        assertThat(productBackend.lastRequest()).hasValueSatisfying(request ->
            assertThat(request.method()).isEqualTo("POST"));
        assertThat(searchBackend.lastRequest()).isEmpty();
    }

    @Test
    @DisplayName("검색 결과 클릭 기록은 같은 경로 · 본문으로 검색 서비스에 넘긴다")
    void routesSearchClicksToSearchService() {
        client.post().uri("/api/v1/search/clicks").header(HttpHeaders.CONTENT_TYPE, "application/json")
            .bodyValue("{\"keyword\":\"원피스\",\"productId\":31,\"rank\":3}").exchange()
            .expectStatus().isOk();

        assertThat(searchBackend.lastRequest()).hasValueSatisfying(request -> {
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.uri()).isEqualTo(URI.create("/api/v1/search/clicks"));
        });
    }

    @Test
    @DisplayName("검색 결과 클릭 경로라도 POST가 아니거나 하위 경로면 404로 응답한다")
    void routesOtherSearchClickRequestsToNotFound() {
        client.get().uri("/api/v1/search/clicks").exchange()
            .expectStatus().isNotFound();
        client.post().uri("/api/v1/search/clicks/1").exchange()
            .expectStatus().isNotFound();

        assertNoBackendRequests();
        assertThat(searchBackend.lastRequest()).isEmpty();
    }

    @Test
    @DisplayName("자녀 프로필 목록 · 하위 경로는 같은 메서드 · 경로로 회원 서비스에 넘긴다")
    void routesChildrenToMemberService() {
        client.get().uri("/api/v1/children").exchange()
            .expectStatus().isOk();
        assertThat(memberBackend.lastRequest()).hasValueSatisfying(request ->
            assertThat(request.uri()).isEqualTo(URI.create("/api/v1/children")));

        client.patch().uri("/api/v1/children/7/growth-records?x=1").exchange()
            .expectStatus().isOk();
        assertThat(memberBackend.lastRequest()).hasValueSatisfying(request -> {
            assertThat(request.method()).isEqualTo("PATCH");
            assertThat(request.uri()).isEqualTo(URI.create("/api/v1/children/7/growth-records?x=1"));
        });
    }

    @Test
    void routesGuardianVerificationToMemberService() {
        client.post().uri("/api/v1/guardian-verification").exchange().expectStatus().isOk();
        assertThat(memberBackend.lastRequest()).hasValueSatisfying(request -> {
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.uri()).isEqualTo(URI.create("/api/v1/guardian-verification"));
        });
    }

    @Test
    @DisplayName("children으로 시작하기만 하는 다른 경로는 404로 응답한다")
    void routesChildrenPrefixLookalikeToNotFound() {
        client.get().uri("/api/v1/childrenx").exchange()
            .expectStatus().isNotFound();

        assertNoBackendRequests();
        assertThat(memberBackend.lastRequest()).isEmpty();
    }

    @Test
    @DisplayName("추천 API 두 개는 같은 메서드 · 경로 · 쿼리로 추천 서비스에 넘긴다")
    void routesRecommendationsToRecommendService() {
        client.get().uri("/api/v1/recommendations/products?childId=11&page=0").exchange()
            .expectStatus().isOk();
        assertThat(recommendBackend.lastRequest()).hasValueSatisfying(request -> {
            assertThat(request.method()).isEqualTo("GET");
            assertThat(request.uri()).isEqualTo(URI.create("/api/v1/recommendations/products?childId=11&page=0"));
        });

        client.get().uri("/api/v1/recommendations/size?productId=31&childId=11").exchange()
            .expectStatus().isOk();
        assertThat(recommendBackend.lastRequest()).hasValueSatisfying(request ->
            assertThat(request.uri()).isEqualTo(URI.create("/api/v1/recommendations/size?productId=31&childId=11")));
    }

    @Test
    @DisplayName("recommendations로 시작하기만 하는 다른 경로는 404로 응답한다")
    void routesRecommendationsPrefixLookalikeToNotFound() {
        client.get().uri("/api/v1/recommendationsx").exchange()
            .expectStatus().isNotFound();

        assertNoBackendRequests();
        assertThat(recommendBackend.lastRequest()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/v1/seller/settlement-targets",
        "/api/v1/seller/settlement-targets/summary",
        "/api/v1/seller/settlements?page=0",
        "/api/v1/seller/settlements/5",
        "/api/v1/admin/settlements",
        "/api/v1/admin/settlements/5/retry",
        "/api/v1/admin/settlements/run",
        "/api/v1/admin/settlement-runs/9",
        "/api/v1/admin/settlement-runs/9/recover",
        "/api/v1/admin/settlement-dead-letters",
        "/api/v1/admin/settlement-dead-letters/3",
        "/api/v1/admin/settlement-dead-letters/3/reprocess"
    })
    @DisplayName("정산 API는 같은 메서드 · 경로 · 쿼리로 정산 서비스에 넘긴다")
    void routesSettlementsToSettlementService(String path) {
        // 역할 검사는 RoleFilterIntegrationTest가 본다. 여기서는 라우팅만 보려고 경로가 요구하는 주체의 토큰을 쓴다
        client.get().uri(path).header(HttpHeaders.AUTHORIZATION, bearerAllowedOn(path)).exchange()
            .expectStatus().isOk();

        assertThat(settlementBackend.lastRequest()).hasValueSatisfying(request ->
            assertThat(request.uri()).isEqualTo(URI.create(path)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/v1/seller/ordersx", "/api/v1/seller/settlementsx", "/api/v1/admin/settlement", "/api/v1/admin/settings"
    })
    @DisplayName("정산과 이름만 비슷한 다른 경로는 404로 응답한다")
    void routesSettlementLookalikesToNotFound(String path) {
        client.get().uri(path).header(HttpHeaders.AUTHORIZATION, bearerAllowedOn(path)).exchange()
            .expectStatus().isNotFound();

        assertNoBackendRequests();
        assertThat(settlementBackend.lastRequest()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/v1/auth/login/kakao?x=1", "/api/v1/auth/logout?x=1", "/api/v1/auth/reissue?x=1",
        "/api/v1/auth/admin/login?x=1", "/api/v1/auth/admin/logout?x=1", "/api/v1/auth/admin/reissue?x=1",
        "/api/v1/dev/tokens?x=1", "/api/v1/members/me?x=1", "/api/v1/members/me/addresses/1?x=1",
        "/api/v1/members/me/seller-application?x=1", "/api/v1/seller/me?x=1",
        "/api/v1/admin/admins/1?x=1", "/api/v1/admin/members/1?x=1",
        "/api/v1/admin/sellers/1/changed-events?x=1", "/api/v1/admin/seller-applications/1?x=1",
        "/api/v1/admin/auth-dead-letters/1?x=1", "/api/v1/admin/member-dead-letters/1?x=1",
        "/api/v1/admin/seller-dead-letters/1?x=1", "/api/v1/admin/child-profile-dead-letters/1?x=1",
        "/api/v1/admin/children/changed-events?x=1"
    })
    @DisplayName("회원 API는 같은 메서드 · 경로 · 쿼리로 회원 서비스에 넘긴다")
    void routesMembersToMemberService(String path) {
        var request = client.patch().uri(path);
        if (path.startsWith("/api/v1/admin/") || path.startsWith("/api/v1/seller/")) {
            request.header(HttpHeaders.AUTHORIZATION, bearerAllowedOn(path));
        }
        request.exchange().expectStatus().isOk();

        assertThat(memberBackend.lastRequest()).hasValueSatisfying(recorded -> {
            assertThat(recorded.method()).isEqualTo("PATCH");
            assertThat(recorded.uri()).isEqualTo(URI.create(path));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/membersx", "/api/v1/seller/ordersx", "/api/v1/admin/memberships"})
    @DisplayName("회원 경로와 이름만 비슷한 경로는 404로 응답한다")
    void routesMemberLookalikesToNotFound(String path) {
        client.get().uri(path).header(HttpHeaders.AUTHORIZATION, bearerAllowedOn(path)).exchange()
            .expectStatus().isNotFound();

        assertNoBackendRequests();
        assertThat(memberBackend.lastRequest()).isEmpty();
    }

    private static String bearerAllowedOn(String path) {
        var now = Instant.now();
        var expiresAt = now.plus(Duration.ofMinutes(30));
        var token = path.startsWith("/api/v1/admin/")
            ? TestTokens.issueAdmin(7L, now, expiresAt)
            : TestTokens.issue(7L, List.of("SELLER"), now, expiresAt);
        return "Bearer " + token;
    }

    @Test
    @DisplayName("결제 · 지갑 · 충전 · 토스 웹훅은 같은 메서드 · 경로 · 쿼리로 결제 서비스에 넘긴다")
    void routesPaymentToPaymentService() {
        client.get().uri("/api/v1/payments?page=0").exchange()
            .expectStatus().isOk();
        assertThat(paymentBackend.lastRequest()).hasValueSatisfying(request ->
            assertThat(request.uri()).isEqualTo(URI.create("/api/v1/payments?page=0")));

        client.get().uri("/api/v1/wallets/me/ledgers?size=1").exchange()
            .expectStatus().isOk();
        assertThat(paymentBackend.lastRequest()).hasValueSatisfying(request ->
            assertThat(request.uri()).isEqualTo(URI.create("/api/v1/wallets/me/ledgers?size=1")));

        client.post().uri("/api/v1/wallets/me/charges/confirm").exchange()
            .expectStatus().isOk();
        assertThat(paymentBackend.lastRequest()).hasValueSatisfying(request -> {
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.uri()).isEqualTo(URI.create("/api/v1/wallets/me/charges/confirm"));
        });

        client.post().uri("/api/v1/webhooks/toss").exchange()
            .expectStatus().isOk();
        assertThat(paymentBackend.lastRequest()).hasValueSatisfying(request ->
            assertThat(request.uri()).isEqualTo(URI.create("/api/v1/webhooks/toss")));
    }

    @Test
    @DisplayName("개발용 예치금 충전은 회원 서비스가 아니라 결제 서비스에 넘긴다")
    void routesDevWalletToPaymentService() {
        client.post().uri("/api/v1/dev/wallets/me/charges").exchange()
            .expectStatus().isOk();

        assertThat(paymentBackend.lastRequest()).hasValueSatisfying(request -> {
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.uri()).isEqualTo(URI.create("/api/v1/dev/wallets/me/charges"));
        });
        assertThat(memberBackend.lastRequest()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/admin/payments", "/api/v1/admin/wallets"})
    @DisplayName("결제 관리자 API는 관리자 토큰이면 결제 서비스에 넘긴다")
    void routesAdminPaymentToPaymentService(String path) {
        client.get().uri(path).header(HttpHeaders.AUTHORIZATION, bearerAllowedOn(path)).exchange()
            .expectStatus().isOk();

        assertThat(paymentBackend.lastRequest()).hasValueSatisfying(request ->
            assertThat(request.uri()).isEqualTo(URI.create(path)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/paymentsx", "/api/v1/walletsx", "/api/v1/webhooks/other"})
    @DisplayName("결제 경로와 이름만 비슷한 다른 경로는 404로 응답한다")
    void routesPaymentLookalikeToNotFound(String path) {
        client.get().uri(path).exchange()
            .expectStatus().isNotFound();

        assertNoBackendRequests();
        assertThat(paymentBackend.lastRequest()).isEmpty();
    }

    @Test
    @DisplayName("공개 상품 상세 · 카테고리 API는 같은 메서드 · 경로 · 쿼리로 상품 서비스에 넘긴다")
    void routesPublicProductApisToProductService() {
        client.get().uri("/api/v1/products/1").exchange()
            .expectStatus().isOk();
        assertThat(productBackend.lastRequest()).hasValueSatisfying(request -> {
            assertThat(request.method()).isEqualTo("GET");
            assertThat(request.uri()).isEqualTo(URI.create("/api/v1/products/1"));
        });

        client.get().uri("/api/v1/categories?x=1").exchange()
            .expectStatus().isOk();
        assertThat(productBackend.lastRequest()).hasValueSatisfying(request ->
            assertThat(request.uri()).isEqualTo(URI.create("/api/v1/categories?x=1")));

        client.get().uri("/api/v1/categories/3/attributes").exchange()
            .expectStatus().isOk();
        assertThat(productBackend.lastRequest()).hasValueSatisfying(request ->
            assertThat(request.uri()).isEqualTo(URI.create("/api/v1/categories/3/attributes")));
        assertThat(searchBackend.lastRequest()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/v1/seller/products?page=0", "/api/v1/seller/products/5", "/api/v1/seller/products/5/sale-status",
        "/api/v1/seller/product-images/upload-urls", "/api/v1/seller/options/9/inventory-adjustments",
        "/api/v1/admin/products/5/sale-status", "/api/v1/admin/products/changed-events",
        "/api/v1/admin/categories", "/api/v1/admin/categories/3", "/api/v1/admin/product-types",
        "/api/v1/admin/attributes/2/values", "/api/v1/admin/measurement-items/4"
    })
    @DisplayName("판매자 · 관리자 상품 API는 그 역할의 토큰이면 같은 메서드 · 경로 · 쿼리로 상품 서비스에 넘긴다")
    void routesSellerAndAdminProductApisToProductService(String path) {
        client.patch().uri(path).header(HttpHeaders.AUTHORIZATION, bearerAllowedOn(path)).exchange()
            .expectStatus().isOk();

        assertThat(productBackend.lastRequest()).hasValueSatisfying(request -> {
            assertThat(request.method()).isEqualTo("PATCH");
            assertThat(request.uri()).isEqualTo(URI.create(path));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/v1/productsx", "/api/v1/categoriesx", "/api/v1/seller/productsx", "/api/v1/admin/productsx",
        "/api/v1/seller/optionsx", "/api/v1/admin/attributesx"
    })
    @DisplayName("상품 경로와 이름만 비슷한 다른 경로는 404로 응답한다")
    void routesProductLookalikesToNotFound(String path) {
        client.get().uri(path).header(HttpHeaders.AUTHORIZATION, bearerAllowedOn(path)).exchange()
            .expectStatus().isNotFound();

        assertNoBackendRequests();
        assertThat(productBackend.lastRequest()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/v1/orders?page=0", "/api/v1/orders/5", "/api/v1/orders/5/items/6/confirm", "/api/v1/orders/5/timeline",
        "/api/v1/cart", "/api/v1/cart/items/3", "/api/v1/wish?childId=2", "/api/v1/wish/items/4",
        "/api/v1/wish/requests/7/approval", "/api/v1/seller/orders?page=0", "/api/v1/seller/orders/9/ship",
        "/api/v1/admin/orders?page=0", "/api/v1/admin/orders/5/history", "/api/v1/admin/orders/stuck/summary",
        "/api/v1/admin/orders/event-failures/1/retry", "/api/v1/admin/orders/recoveries/1/cancel",
        "/api/v1/admin/orders/dead-letters/1/reprocess", "/api/v1/admin/orders/outbox/quarantined",
        "/api/v1/admin/orders/purchase-confirmed-events"
    })
    @DisplayName("주문 · 장바구니 · 조르기 · 판매자 주문 · 주문 관리자 API는 같은 메서드 · 경로 · 쿼리로 주문 서비스에 넘긴다")
    void routesOrderApisToOrderService(String path) {
        var request = client.patch().uri(path);
        if (path.startsWith("/api/v1/admin/") || path.startsWith("/api/v1/seller/")) {
            request.header(HttpHeaders.AUTHORIZATION, bearerAllowedOn(path));
        }
        request.exchange().expectStatus().isOk();

        assertThat(orderBackend.lastRequest()).hasValueSatisfying(recorded -> {
            assertThat(recorded.method()).isEqualTo("PATCH");
            assertThat(recorded.uri()).isEqualTo(URI.create(path));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/v1/ordersx", "/api/v1/carts", "/api/v1/wishlist", "/api/v1/seller/ordersx", "/api/v1/admin/ordersx"
    })
    @DisplayName("주문 경로와 이름만 비슷한 다른 경로는 404로 응답한다")
    void routesOrderLookalikesToNotFound(String path) {
        client.get().uri(path).header(HttpHeaders.AUTHORIZATION, bearerAllowedOn(path)).exchange()
            .expectStatus().isNotFound();

        assertNoBackendRequests();
        assertThat(orderBackend.lastRequest()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/v1/reviews?productId=1", "/api/v1/reviews/3", "/api/v1/reviews/me", "/api/v1/reviews/summary?productId=1",
        "/api/v1/reviews/eligible", "/api/v1/favorites", "/api/v1/favorites/5", "/api/v1/favorites/summary?productId=5",
        "/api/v1/admin/community/dead-letters", "/api/v1/admin/community/dead-letters/2/reprocess"
    })
    @DisplayName("리뷰 · 찜 · 커뮤니티 관리자 API는 같은 메서드 · 경로 · 쿼리로 커뮤니티 서비스에 넘긴다")
    void routesCommunityApisToCommunityService(String path) {
        var request = client.patch().uri(path);
        if (path.startsWith("/api/v1/admin/")) {
            request.header(HttpHeaders.AUTHORIZATION, bearerAllowedOn(path));
        }
        request.exchange().expectStatus().isOk();

        assertThat(communityBackend.lastRequest()).hasValueSatisfying(recorded -> {
            assertThat(recorded.method()).isEqualTo("PATCH");
            assertThat(recorded.uri()).isEqualTo(URI.create(path));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/reviewsx", "/api/v1/favoritesx", "/api/v1/admin/communityx"})
    @DisplayName("커뮤니티 경로와 이름만 비슷한 다른 경로는 404로 응답한다")
    void routesCommunityLookalikesToNotFound(String path) {
        client.get().uri(path).header(HttpHeaders.AUTHORIZATION, bearerAllowedOn(path)).exchange()
            .expectStatus().isNotFound();

        assertNoBackendRequests();
        assertThat(communityBackend.lastRequest()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/v1/notifications?page=0&size=20", "/api/v1/notifications/unread-count", "/api/v1/notifications/3/read"
    })
    @DisplayName("알림함 API는 같은 메서드 · 경로 · 쿼리로 알림 서비스에 넘긴다")
    void routesNotificationApisToNotificationService(String path) {
        client.patch().uri(path).exchange()
            .expectStatus().isOk();

        assertThat(notificationBackend.lastRequest()).hasValueSatisfying(recorded -> {
            assertThat(recorded.method()).isEqualTo("PATCH");
            assertThat(recorded.uri()).isEqualTo(URI.create(path));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/v1/admin/notification-dead-letters", "/api/v1/admin/notification-dead-letters/3",
        "/api/v1/admin/notification-dead-letters/3/reprocess"
    })
    @DisplayName("알림 관리자 API(DLT)는 같은 메서드 · 경로 · 쿼리로 알림 서비스에 넘긴다")
    void routesNotificationAdminApisToNotificationService(String path) {
        client.get().uri(path).header(HttpHeaders.AUTHORIZATION, bearerAllowedOn(path)).exchange()
            .expectStatus().isOk();

        assertThat(notificationBackend.lastRequest()).hasValueSatisfying(recorded ->
            assertThat(recorded.uri()).isEqualTo(URI.create(path)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/v1/child/notifications?page=0&size=20", "/api/v1/child/notifications/unread-count",
        "/api/v1/child/notifications/3/read", "/api/v1/child/notifications/read-all"
    })
    @DisplayName("자녀 알림함 API는 자녀 토큰이면 같은 메서드 · 경로 · 쿼리로 알림 서비스에 넘긴다")
    void routesChildNotificationApisToNotificationService(String path) {
        var now = Instant.now();
        var token = TestTokens.issueChild(7001, 8, now, now.plusSeconds(1800));
        client.patch().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange()
            .expectStatus().isOk();

        assertThat(notificationBackend.lastRequest()).hasValueSatisfying(recorded -> {
            assertThat(recorded.method()).isEqualTo("PATCH");
            assertThat(recorded.uri()).isEqualTo(URI.create(path));
        });
    }

    @Test
    @DisplayName("알림함 경로와 이름만 비슷한 다른 경로는 404로 응답한다")
    void routesNotificationLookalikesToNotFound() {
        client.get().uri("/api/v1/notificationsx").exchange()
            .expectStatus().isNotFound();

        assertNoBackendRequests();
        assertThat(notificationBackend.lastRequest()).isEmpty();
    }

    @Test
    @DisplayName("라우트에 없는 경로는 404 ProblemDetail이고 넘기지 않는다")
    void rejectsUnroutedPath() {
        expectNotFound(client.get().uri("/api/v1/unrouted?x=1").exchange(), "/api/v1/unrouted");
    }

    @Test
    @DisplayName("프리플라이트는 Gateway가 답하고 넘기지 않는다")
    void answersPreflight() {
        preflight(ORIGIN)
            .expectStatus().isOk()
            .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN)
            .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");

    }

    @Test
    @DisplayName("허용하지 않은 origin의 프리플라이트는 403이다")
    void rejectsPreflightFromUnknownOrigin() {
        preflight("https://evil.example")
            .expectStatus().isForbidden();

    }

    @Test
    @DisplayName("Gateway가 만든 401에도 CORS 헤더가 붙는다")
    void addsCorsHeadersToUnauthorized() {
        client.get().uri("/api/v1/products")
            .header(HttpHeaders.ORIGIN, ORIGIN)
            .header(HttpHeaders.AUTHORIZATION, "Bearer abc")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN)
            .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
    }

    @Test
    @DisplayName("서비스 응답에 Gateway가 CORS 헤더를 붙인다(서비스는 CORS를 하지 않는다)")
    void addsCorsHeadersToBackendResponse() {
        corsGet()
            .expectStatus().isOk()
            .expectHeader().values(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                values -> assertThat(values).containsExactly(ORIGIN))
            .expectHeader().values(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                values -> assertThat(values).containsExactly("true"))
            .expectHeader().values(HttpHeaders.VARY, values -> assertThat(values).contains(HttpHeaders.ORIGIN));
    }

    @Test
    @DisplayName("프론트가 traceId · 429의 Retry-After를 읽을 수 있게 X-Request-Id · Retry-After를 노출한다")
    void exposesRequestId() {
        corsGet()
            .expectStatus().isOk()
            .expectHeader().values(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                values -> assertThat(values).containsExactly("X-Request-Id, Retry-After"));
    }

    @Test
    @DisplayName("nginx가 넣은 Host · X-Forwarded-*를 앱이 nginx 직결 때와 같은 값으로 받는다")
    void forwardsNginxHeaders() {
        client.get().uri("/api/v1/products/1")
            .headers(this::nginxHeaders)
            .exchange()
            .expectStatus().isOk();

        assertThat(productBackend.lastRequest()).hasValueSatisfying(request -> {
            assertThat(request.header(HttpHeaders.HOST)).containsExactly(NGINX_HOST);
            // Tomcat은 오른쪽부터 내부 프록시(사설 · 루프백)를 건너뛴 첫 값을 클라이언트로 본다
            var forwardedFor = request.header("X-Forwarded-For").stream()
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(String::trim)
                .toList();
            assertThat(forwardedFor).first().isEqualTo(CLIENT_IP);
            assertThat(forwardedFor.subList(1, forwardedFor.size())).allMatch(RoutingIntegrationTest::isInternal);
            // 값이 여럿이면(https,http) Tomcat이 https로 보지 않는다
            assertThat(request.header("X-Forwarded-Proto")).containsExactly("https");
            assertThat(request.header("X-Forwarded-Host")).containsExactly(NGINX_HOST);
            assertThat(request.header("X-Forwarded-Port")).containsExactly("443");
            // nginx는 Forwarded를 넣지 않는다. Gateway도 만들지 않는다
            assertThat(request.header("Forwarded")).isEmpty();
        });
    }

    @Test
    @DisplayName("nginx 뒤의 같은 출처 요청(dev Swagger)은 CORS로 막지 않는다")
    void treatsSameOriginBehindNginxAsSameOrigin() {
        client.post().uri("/api/v1/orders")
            .headers(this::nginxHeaders)
            .header(HttpHeaders.ORIGIN, "https://" + NGINX_HOST)
            .exchange()
            .expectStatus().isOk();

        assertThat(orderBackend.lastRequest()).isPresent();
    }

    private ResponseSpec preflight(String origin) {
        return client.options().uri("/api/v1/orders")
            .header(HttpHeaders.ORIGIN, origin)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
            .exchange();
    }

    private ResponseSpec corsGet() {
        return client.get().uri("/api/v1/products")
            .header(HttpHeaders.ORIGIN, ORIGIN)
            .exchange();
    }

    // deploy/nginx/kidly.conf.template 공통 설정이 넣는 헤더
    private void nginxHeaders(HttpHeaders headers) {
        headers.set(HttpHeaders.HOST, NGINX_HOST);
        headers.set("X-Real-IP", CLIENT_IP);
        headers.set("X-Forwarded-For", CLIENT_IP);
        headers.set("X-Forwarded-Proto", "https");
        headers.set("X-Forwarded-Host", NGINX_HOST);
        headers.set("X-Forwarded-Port", "443");
    }

    private static boolean isInternal(String ip) {
        try {
            var address = InetAddress.getByName(ip);
            return address.isLoopbackAddress() || address.isSiteLocalAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
