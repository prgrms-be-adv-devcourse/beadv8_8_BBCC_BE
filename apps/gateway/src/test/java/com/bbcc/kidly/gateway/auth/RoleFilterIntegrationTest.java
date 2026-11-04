package com.bbcc.kidly.gateway.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.gateway.support.GatewayTestSupport;
import com.bbcc.kidly.gateway.support.IntegrationTest;
import com.bbcc.kidly.gateway.support.RecordingBackend;
import com.bbcc.kidly.gateway.support.TestTokens;
import com.bbcc.kidly.shared.auth.MemberHeaders;
import com.bbcc.kidly.shared.auth.PrincipalHeaders;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

/**
 * /api/v1/admin/**는 관리자, /api/v1/seller/**는 SELLER 회원만 넘기는지 본다(#638).
 * 토큰이 없으면 401, 역할이 없으면 403이고 둘 다 백엔드로 넘기지 않는다.
 */
@IntegrationTest
class RoleFilterIntegrationTest extends GatewayTestSupport {

    private static final String ADMIN_PATH = "/api/v1/admin/orders";
    private static final String SELLER_PATH = "/api/v1/seller/products";
    private static final String ORIGIN = RecordingBackend.ALLOWED_ORIGIN;

    @ParameterizedTest
    @ValueSource(strings = {ADMIN_PATH, SELLER_PATH})
    @DisplayName("토큰 없이 관리자 · 판매자 API를 부르면 401이고 넘기지 않는다")
    void rejectsWithoutToken(String path) {
        expectUnauthorized(client.get().uri(path).exchange(), path);
    }

    @Test
    @DisplayName("밖에서 위조한 X-Member-* 헤더만으로는 관리자 API를 부를 수 없다(401)")
    void rejectsForgedMemberHeaders() {
        expectUnauthorized(client.get().uri(ADMIN_PATH)
            .header(MemberHeaders.MEMBER_ID, "1")
            .header(MemberHeaders.MEMBER_ROLES, "ADMIN")
            .exchange(), ADMIN_PATH);
    }

    @ParameterizedTest
    @ValueSource(strings = {ADMIN_PATH, SELLER_PATH})
    @DisplayName("BUYER 토큰으로 관리자 · 판매자 API를 부르면 403이고 넘기지 않는다")
    void rejectsBuyer(String path) {
        expectForbidden(get(path, "BUYER"), path);
    }

    @Test
    @DisplayName("SELLER 토큰으로 관리자 API를 부르면 403이다")
    void rejectsSellerOnAdminApi() {
        expectForbidden(get(ADMIN_PATH, "SELLER"), ADMIN_PATH);
    }

    @Test
    @DisplayName("SELLER 토큰이면 판매자 API를 넘긴다")
    void passesSeller() {
        get(SELLER_PATH, "BUYER", "SELLER").expectStatus().isOk();

        assertThat(productBackend.lastRequest()).hasValueSatisfying(
            request -> assertThat(request.uri().getPath()).isEqualTo(SELLER_PATH));
    }

    @Test
    @DisplayName("관리자 토큰이면 관리자 API를 넘긴다")
    void passesAdmin() {
        getAdmin(ADMIN_PATH).expectStatus().isOk();

        assertThat(orderBackend.lastRequest()).hasValueSatisfying(
            request -> assertThat(request.uri().getPath()).isEqualTo(ADMIN_PATH));
    }

    @Test
    @DisplayName("그 밖의 경로는 토큰이 없어도 넘긴다(로그인이 필요한지는 서비스가 정한다)")
    void passesOtherPathsWithoutToken() {
        client.get().uri("/api/v1/products/1").exchange()
            .expectStatus().isOk();

        assertThat(productBackend.lastRequest()).isPresent();
    }

    // 서비스(Spring Security requestMatchers("/api/v1/admin/**"))와 같다: /**는 0개 이상의 경로 조각이라 /api/v1/admin도 맞는다
    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/admin", "/api/v1/admin/", "/api/v1/seller"})
    @DisplayName("꼬리 없는 /api/v1/admin · /api/v1/seller도 역할을 검사한다")
    void checksPrefixItself(String path) {
        expectUnauthorized(client.get().uri(path).exchange(), path);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/administrator", "/api/v1/sellers/1"})
    @DisplayName("이름이 admin · seller로 시작할 뿐인 다른 경로는 역할을 검사하지 않는다")
    void ignoresPathsSharingPrefix(String path) {
        client.get().uri(path).exchange()
            .expectStatus().isOk();

        assertThat(backend.lastRequest()).isPresent();
    }

    @Test
    @DisplayName("Gateway가 만든 401에도 CORS 헤더가 붙는다")
    void addsCorsHeadersToUnauthorized() {
        client.get().uri(ADMIN_PATH)
            .header(HttpHeaders.ORIGIN, ORIGIN)
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN)
            .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
    }

    @Test
    @DisplayName("Gateway가 만든 403에도 CORS 헤더가 붙는다")
    void addsCorsHeadersToForbidden() {
        client.get().uri(ADMIN_PATH)
            .header(HttpHeaders.ORIGIN, ORIGIN)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("BUYER"))
            .exchange()
            .expectStatus().isForbidden()
            .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN)
            .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
    }

    @Test
    @DisplayName("ADMIN 역할이 있어도 회원 토큰으로 관리자 API를 부르면 403이다")
    void rejectsMemberAdminRole() {
        expectForbidden(get(ADMIN_PATH, "BUYER", "ADMIN"), ADMIN_PATH);
    }

    @Test
    @DisplayName("관리자 토큰으로 판매자 API를 부르면 403이다")
    void rejectsAdminOnSellerPath() {
        expectForbidden(getAdmin(SELLER_PATH), SELLER_PATH);
    }

    @Test
    @DisplayName("밖에서 위조한 X-Admin-Id 헤더만으로는 관리자 API를 부를 수 없다(401)")
    void rejectsForgedAdminHeader() {
        expectUnauthorized(client.get().uri(ADMIN_PATH)
            .header(PrincipalHeaders.ADMIN_ID, "7")
            .exchange(), ADMIN_PATH);
    }

    @Test
    void childPathsRequireChildPrincipal() {
        var path = "/api/v1/child/orders";
        expectUnauthorized(client.get().uri(path).exchange(), path);
        expectForbidden(get(path, "BUYER"), path);
        expectForbidden(getAdmin(path), path);
        var now = Instant.now();
        var token = TestTokens.issueChild(7001, 8, now, now.plusSeconds(1800));
        client.get().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange().expectStatus().isOk();
        backend.reset();
        for (var forbidden : List.of(ADMIN_PATH, SELLER_PATH)) {
            expectForbidden(client.get().uri(forbidden).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange(), forbidden);
        }
        client.get().uri("/api/v1/children").exchange().expectStatus().isOk();
    }

    @Test
    void childProfileRequiresChildPrincipal() {
        var path = "/api/v1/child/profile";
        expectUnauthorized(client.get().uri(path).exchange(), path);
        expectForbidden(get(path, "BUYER"), path);
        expectForbidden(getAdmin(path), path);
    }

    private ResponseSpec getAdmin(String path) {
        var now = Instant.now();
        return client.get().uri(path)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.issueAdmin(7, now, now.plusSeconds(1800)))
            .exchange();
    }

    private ResponseSpec get(String path, String... roles) {
        return client.get().uri(path)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(roles))
            .exchange();
    }

    private static String token(String... roles) {
        var now = Instant.now();
        return TestTokens.issue(7L, List.of(roles), now, now.plus(Duration.ofMinutes(30)));
    }
}
